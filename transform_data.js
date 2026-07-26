const fs = require('fs');

const inputFile = 'c:\\Users\\Pc casa\\Downloads\\mis-ahorros-37a68-default-rtdb--OdUYvHFp75C_hPgl0Ua-export.json';
const outputFile = 'c:\\Users\\Pc casa\\Downloads\\kilometros_export_ready.json';
const newVehicleId = '03Z029l0u2ffvU2siSqX';

// Leer el archivo JSON original
const rawData = fs.readFileSync(inputFile, 'utf-8');
const data = JSON.parse(rawData);

// Convertir el objeto en un array para poder ordenarlo
const entries = Object.values(data);

// Ordenar por odómetro (o fecha) para asegurar un orden cronológico y calcular diferencias
entries.sort((a, b) => a.odometro - b.odometro);

const transformedEntries = [];
let previousOdometer = 0;

for (const entry of entries) {
    if (entry.tipo !== "COMBUSTIBLE") continue;

    const kmTraveled = previousOdometer > 0 ? Math.max(0, entry.odometro - previousOdometer) : 0;
    const gallons = entry.galones;
    const litersPerGallon = 3.78541;
    const liters = gallons * litersPerGallon;
    const pricePerGallon = gallons > 0 ? entry.costoTotal / gallons : 0;
    const pricePerLiter = pricePerGallon / litersPerGallon;
    
    const kmPerGallon = (gallons > 0 && kmTraveled > 0) ? kmTraveled / gallons : 0;
    const kmPerLiter = kmPerGallon / litersPerGallon;

    const isFullTank = entry.esTanqueLleno === true;
    const isPartial = !isFullTank; // Asumimos parcial si no es lleno, ya que no teníamos 'Reserva' antes
    const isReserve = false;

    const expense = {
        id: entry.id,
        vehicleId: newVehicleId,
        type: "FUEL",
        amount: entry.costoTotal,
        date: entry.fecha,
        notes: entry.notas || "",
        fuelDetails: {
            odometerAtRefuel: entry.odometro,
            previousOdometer: previousOdometer,
            kmTraveled: kmTraveled,
            gallons: gallons,
            liters: liters,
            pricePerGallon: pricePerGallon,
            pricePerLiter: pricePerLiter,
            enteredUnit: "GALLON",
            enteredQuantity: gallons,
            pricePerEnteredUnit: pricePerGallon,
            kmPerGallon: kmPerGallon,
            kmPerLiter: kmPerLiter,
            isReserve: isReserve,
            isFullTank: isFullTank,
            isPartial: isPartial
        }
    };

    transformedEntries.push(expense);
    previousOdometer = entry.odometro;
}

// Convertir de nuevo a un objeto con los IDs como keys (estructura de Firestore para importar)
// o simplemente guardarlo como array dependiendo de cómo se vaya a subir.
// Para facilitar la subida con un script de Firebase Admin, un array o un mapa funciona.
const firestoreReadyData = {};
transformedEntries.forEach(exp => {
    firestoreReadyData[exp.id] = exp;
});

fs.writeFileSync(outputFile, JSON.stringify(firestoreReadyData, null, 2), 'utf-8');

console.log(`¡Transformación completa! Se procesaron ${transformedEntries.length} registros.`);
console.log(`El archivo listo para subir se guardó en: ${outputFile}`);
