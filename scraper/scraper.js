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

// Guardar datos en Firestore en documento pico_y_placa para desarrollo
async function guardarEnFirestore(data) {
  try {
    await db.collection('configuracion').doc('pico_y_placa').set(data);
    console.log("¡Datos actualizados con éxito en Firebase Firestore (pico_y_placa)!");
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

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Scrapear una ciudad colombiana desde su página principal
async function scrapeCityData(city) {
  try {
    const url = `https://www.pyphoy.com/${city.id}`;
    console.log(`Scrapeando ciudad: ${city.name} (${url})`);
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      },
      timeout: 8000
    });
    
    const $ = cheerio.load(response.data);
    const restrictions = [];
    
    // Obtener departamento/estado
    let department = "Colombia";
    const deptText = $('.text-sm.text-gray-500.font-medium').text().trim();
    if (deptText) {
      department = deptText.split(',')[0].trim();
    }

    // Buscar las tarjetas de categorías (particulares, motos, taxis)
    $('.shrink-0.bg-blue-500, .bg-blue-500').each((i, el) => {
      const card = $(el).closest('.flex-col');
      if (card.length === 0) return;
      
      const link = $(el).find('a');
      if (link.length === 0) return;
      
      const categoryName = link.text().replace(/[🚗🛵🚕🚛🚐🚐🚌]/g, '').trim();
      let vehicle_type = "";
      const catLower = categoryName.toLowerCase();
      
      if (catLower.includes('particular')) {
        vehicle_type = "PARTICULAR";
      } else if (catLower.includes('moto')) {
        vehicle_type = "MOTO";
      } else if (catLower.includes('taxi')) {
        vehicle_type = "TAXI";
      } else {
        return; // Ignorar otras categorías (carga, especial, etc.)
      }

      // Buscar el horario en la tarjeta
      let scheduleText = "";
      card.find('div').each((j, divEl) => {
        const txt = $(divEl).text().trim();
        if (/^\d{1,2}:\d{2}(?:am|pm)?\s*a\s*\d{1,2}:\d{2}(?:am|pm)?$/i.test(txt)) {
          scheduleText = txt;
          return false;
        }
      });

      let algorithm = "WEEKDAY_MAP";
      let schedule = "6:00 - 20:00";
      if (city.id === 'bogota' && vehicle_type === 'PARTICULAR') {
        algorithm = "BOGOTA_PARITY";
      }
      
      if (scheduleText) {
        schedule = formatHorario(scheduleText);
      }

      // Reglas de paridad semanales por defecto
      let weekday_rules = {
        "1": [1, 2], // Lunes
        "2": [3, 4], // Martes
        "3": [5, 6], // Miércoles
        "4": [7, 8], // Jueves
        "5": [9, 0]  // Viernes
      };

      // Conservar reglas específicas exactas ya probadas
      if (city.id === "bogota") {
        if (vehicle_type === "TAXI") {
          weekday_rules = {
            "1": [1, 2], "2": [3, 4], "3": [5, 6], "4": [7, 8], "5": [9, 0], "6": [1, 2]
          };
        }
      } else if (city.id === "medellin") {
        if (vehicle_type === "PARTICULAR" || vehicle_type === "MOTO") {
          weekday_rules = {
            "1": [0, 1], "2": [2, 3], "3": [4, 5], "4": [6, 7], "5": [8, 9]
          };
        } else if (vehicle_type === "TAXI") {
          weekday_rules = {
            "1": [9], "2": [0], "3": [1], "4": [2], "5": [3]
          };
        }
      } else if (city.id === "cali") {
        if (vehicle_type === "PARTICULAR") {
          weekday_rules = {
            "1": [3, 4], "2": [5, 6], "3": [7, 8], "4": [9, 0], "5": [1, 2]
          };
        }
      }

      restrictions.push({
        vehicle_type: vehicle_type,
        algorithm: algorithm,
        schedule: schedule,
        description: `Restricción para vehículos tipo ${categoryName} en ${city.name}. Horario: ${schedule}.`,
        days: ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"],
        weekday_rules: weekday_rules
      });
    });

    if (restrictions.length > 0) {
      return {
        id: city.id,
        name: city.name,
        state: department,
        source_url: url,
        restrictions: restrictions
      };
    }
  } catch (err) {
    console.warn(`Error al scrapear ciudad ${city.name}: ${err.message}`);
  }
  return null;
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
      "2026-07-13", // Festivo Especial (Nuevo festivo del 13 de Julio)
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

  try {
    console.log("Obteniendo lista completa de ciudades desde pyphoy.com...");
    const homeResponse = await axios.get('https://www.pyphoy.com/', {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      },
      timeout: 10000
    });
    const $ = cheerio.load(homeResponse.data);
    const citiesToScrape = [];

    // Capturar todos los option de la lista de ciudades
    $('#city option').each((i, el) => {
      const slug = $(el).val();
      const name = $(el).text().trim();
      // Excluir opciones vacías y ciudades fuera de Colombia
      if (slug && slug !== "" && !['cochabamba', 'el-alto', 'la-paz', 'potosi', 'sucre', 'quito', 'santiago', 'san-jose'].includes(slug)) {
        citiesToScrape.push({ id: slug, name: name });
      }
    });

    console.log(`Se encontraron ${citiesToScrape.length} ciudades colombianas. Iniciando crawling...`);

    for (const city of citiesToScrape) {
      const cityData = await scrapeCityData(city);
      if (cityData) {
        dataFinal.cities.push(cityData);
      }
      await sleep(250); // Delay prudente de 250ms para evitar rate limits
    }

    // Guardar estructura completa en Firestore
    await guardarEnFirestore(dataFinal);
  } catch (err) {
    console.error("Error fatal en el scraper:", err);
    process.exit(1);
  }
}

run();
