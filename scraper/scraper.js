const admin = require('firebase-admin');
const axios = require('axios');
const cheerio = require('cheerio');
const fs = require('fs');
const path = require('path');

// Inicializar Firebase: variable de entorno (GitHub Actions) o local firebase-key.json
let serviceAccount;
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  try {
    serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  } catch (err) {
    console.error("Error al parsear FIREBASE_SERVICE_ACCOUNT JSON:", err.message);
    process.exit(1);
  }
} else {
  const localKeyPath = path.join(__dirname, 'firebase-key.json');
  if (fs.existsSync(localKeyPath)) {
    console.log("Cargando credenciales de Firebase desde archivo local firebase-key.json");
    serviceAccount = require(localKeyPath);
  } else {
    console.error("Falta la variable de entorno FIREBASE_SERVICE_ACCOUNT y no se encontró firebase-key.json local");
    process.exit(1);
  }
}

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// Guardar datos en Firestore en documento pico_y_placa_dev para desarrollo
async function guardarEnFirestore(data) {
  try {
    await db.collection('configuracion').doc('pico_y_placa_dev').set(data);
    console.log("¡Datos actualizados con éxito en Firebase Firestore (pico_y_placa_dev)!");
  } catch (error) {
    console.error("Error al guardar en Firestore:", error);
    process.exit(1);
  }
}

// Convertir hora tipo "6:00am a 9:00pm" a "6:00 - 21:00"
function convertTo24h(timeStr) {
  const isPm = timeStr.includes('pm');
  const cleanStr = timeStr.replace('am', '').replace('pm', '').trim();
  const parts = cleanStr.split(':');
  let hours = parseInt(parts[0], 10);
  const minutes = parts.length > 1 ? parts[1] : "00";
  if (isPm && hours < 12) {
    hours += 12;
  }
  if (!isPm && hours === 12) {
    hours = 0;
  }
  return `${hours}:${minutes}`;
}

function formatHorario(rawText) {
  const parts = rawText.toLowerCase().split('a').map(p => p.trim());
  if (parts.length === 2) {
    const start = convertTo24h(parts[0]);
    const end = convertTo24h(parts[1]);
    return `${start} - ${end}`;
  }
  return rawText;
}

// Scrapear horario de pyphoy.com
async function scrapeHorario(citySlug, categorySlug, fallbackValue) {
  try {
    const url = `https://www.pyphoy.com/${citySlug}/${categorySlug}`;
    console.log(`Scrapeando horario desde: ${url}`);
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      },
      timeout: 8000
    });
    const $ = cheerio.load(response.data);
    let horarioText = "";
    $('div').each((i, el) => {
      const txt = $(el).text().trim();
      // regex para capturar franjas horarias tipo "6:00am a 9:00pm" o "5:00am a 8:00pm"
      if (/^\d{1,2}:\d{2}(?:am|pm)?\s*a\s*\d{1,2}:\d{2}(?:am|pm)?$/i.test(txt)) {
        horarioText = txt;
        return false;
      }
    });

    if (horarioText) {
      const formatted = formatHorario(horarioText);
      console.log(`Scrapeado exitosamente para ${citySlug}/${categorySlug}: ${formatted}`);
      return formatted;
    }
  } catch (err) {
    console.warn(`No se pudo scrapear horario para ${citySlug}/${categorySlug} (${err.message}). Usando fallback: ${fallbackValue}`);
  }
  return fallbackValue;
}

async function run() {
  const dataFinal = {
    schema_version: "1.0",
    last_updated: new Date().toISOString(),
    holidays: [
      "2026-01-01", // Año Nuevo
      "2026-01-12", // Reyes Magos
      "2026-03-23", // San José
      "2026-04-02", // Jueves Santo
      "2026-04-03", // Viernes Santo
      "2026-05-01", // Día del Trabajo
      "2026-05-18", // Ascensión
      "2026-06-08", // Corpus Christi
      "2026-06-15", // Sagrado Corazón
      "2026-06-29", // San Pedro y San Pablo
      "2026-07-20", // Independencia
      "2026-08-07", // Batalla de Boyacá
      "2026-08-17", // Asunción
      "2026-10-12", // Día de la Raza
      "2026-11-02", // Todos los Santos
      "2026-11-16", // Independencia de Cartagena
      "2026-12-08", // Inmaculada Concepción
      "2026-12-25"  // Navidad
    ],
    cities: []
  };

  // --- CIUDAD 1: BOGOTÁ D.C. ---
  console.log("Procesando Bogotá D.C...");
  const bogotaParticularesSchedule = await scrapeHorario("bogota", "particulares", "6:00 - 21:00");
  const bogotaTaxisSchedule = await scrapeHorario("bogota", "taxis", "6:00 - 21:00");

  dataFinal.cities.push({
    id: "bogota",
    name: "Bogotá D.C.",
    state: "Cundinamarca",
    source_url: "https://www.pyphoy.com/bogota",
    restrictions: [
      {
        vehicle_type: "PARTICULAR",
        algorithm: "BOGOTA_PARITY",
        schedule: bogotaParticularesSchedule,
        description: `En Bogotá D.C., los días marcados en rojo no puedes circular con tu vehículo particular durante: ${bogotaParticularesSchedule}. Recuerda que la restricción funciona por paridad (días pares restringen placas 1-5; días impares restringen placas 6-0). En festivos no aplica.`,
        days: ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"]
      },
      {
        vehicle_type: "TAXI",
        algorithm: "WEEKDAY_MAP",
        schedule: bogotaTaxisSchedule,
        description: `Restricción para taxis en Bogotá D.C. de lunes a sábado de ${bogotaTaxisSchedule} según el último número de la placa. Festivos libres.`,
        days: ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"],
        weekday_rules: {
          "1": [1, 2], // Lunes
          "2": [3, 4], // Martes
          "3": [5, 6], // Miércoles
          "4": [7, 8], // Jueves
          "5": [9, 0], // Viernes
          "6": [1, 2]  // Sábado
        }
      }
    ]
  });

  // --- CIUDAD 2: MEDELLÍN ---
  console.log("Procesando Medellín...");
  const medellinParticularesSchedule = await scrapeHorario("medellin", "particulares", "5:00 - 20:00");
  const medellinMotosSchedule = await scrapeHorario("medellin", "motos", "5:00 - 20:00");
  const medellinTaxisSchedule = await scrapeHorario("medellin", "taxis", "6:00 - 20:00");

  dataFinal.cities.push({
    id: "medellin",
    name: "Medellín",
    state: "Antioquia",
    source_url: "https://www.pyphoy.com/medellin",
    restrictions: [
      {
        vehicle_type: "PARTICULAR",
        algorithm: "WEEKDAY_MAP",
        schedule: medellinParticularesSchedule,
        description: `En Medellín, los vehículos particulares tienen restricción de lunes a viernes de ${medellinParticularesSchedule} según el último dígito de la placa.`,
        days: ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"],
        weekday_rules: {
          "1": [0, 1], // Lunes
          "2": [2, 3], // Martes
          "3": [4, 5], // Miércoles
          "4": [6, 7], // Jueves
          "5": [8, 9]  // Viernes
        }
      },
      {
        vehicle_type: "MOTO",
        algorithm: "WEEKDAY_MAP",
        schedule: medellinMotosSchedule,
        description: `En Medellín, las motos de 2 y 4 tiempos tienen restricción de lunes a viernes de ${medellinMotosSchedule} según el primer número de la placa.`,
        days: ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"],
        weekday_rules: {
          "1": [0, 1],
          "2": [2, 3],
          "3": [4, 5],
          "4": [6, 7],
          "5": [8, 9]
        }
      },
      {
        vehicle_type: "TAXI",
        algorithm: "WEEKDAY_MAP",
        schedule: medellinTaxisSchedule,
        description: `En Medellín, los taxis tienen restricción de lunes a viernes de ${medellinTaxisSchedule} según el último número de la placa.`,
        days: ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"],
        weekday_rules: {
          "1": [9],
          "2": [0],
          "3": [1],
          "4": [2],
          "5": [3]
        }
      }
    ]
  });

  // --- CIUDAD 3: CALI ---
  console.log("Procesando Cali...");
  const caliParticularesSchedule = await scrapeHorario("cali", "particulares", "6:00 - 20:00");

  dataFinal.cities.push({
    id: "cali",
    name: "Cali",
    state: "Valle del Cauca",
    source_url: "https://www.pyphoy.com/cali",
    restrictions: [
      {
        vehicle_type: "PARTICULAR",
        algorithm: "WEEKDAY_MAP",
        schedule: caliParticularesSchedule,
        description: `En Cali, los vehículos particulares tienen restricción de lunes a viernes de ${caliParticularesSchedule} según el último número de la placa.`,
        days: ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"],
        weekday_rules: {
          "1": [3, 4], // Lunes
          "2": [5, 6], // Martes
          "3": [7, 8], // Miércoles
          "4": [9, 0], // Jueves
          "5": [1, 2]  // Viernes
        }
      }
    ]
  });

  // Guardar estructura en Firestore
  await guardarEnFirestore(dataFinal);
}

run();
