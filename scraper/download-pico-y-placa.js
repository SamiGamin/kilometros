const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

const keyPath = path.join(__dirname, 'firebase-key.json');
if (!fs.existsSync(keyPath)) {
  console.error("No se encontró el archivo de credenciales 'firebase-key.json'.");
  process.exit(1);
}

const serviceAccount = require(keyPath);

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

const db = admin.firestore();

async function download() {
  try {
    console.log("Descargando configuración de 'pico_y_placa' desde Firestore...");
    const docRef = db.collection('configuracion').doc('pico_y_placa');
    const docSnap = await docRef.get();

    if (!docSnap.exists) {
      console.error("El documento 'configuracion/pico_y_placa' no existe en Firestore.");
      process.exit(1);
    }

    const data = docSnap.data();
    const exportPath = path.join(__dirname, 'pico-y-placa-export.json');
    fs.writeFileSync(exportPath, JSON.stringify(data, null, 2), 'utf8');

    console.log(`¡Datos guardados con éxito en ${exportPath}!`);
    console.log(`- last_updated: ${data.last_updated}`);
    console.log(`- holidays.length: ${data.holidays ? data.holidays.length : 0}`);
    console.log(`- cities.length: ${data.cities ? data.cities.length : 0}`);
  } catch (err) {
    console.error("Error al descargar datos de Firestore:", err.message);
    process.exit(1);
  }
}

download();
