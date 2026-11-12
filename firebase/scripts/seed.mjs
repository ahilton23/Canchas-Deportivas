import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';

const local = process.argv.includes('--local');
const project = process.env.GCLOUD_PROJECT || 'demo-canchas-spark';
if (local) {
  if (!project.startsWith('demo-')) throw new Error('--local requiere un proyecto demo- para evitar modificar la nube.');
  process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
  process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
  initializeApp({ projectId: project });
} else {
  if (project.startsWith('demo-')) throw new Error('Define GCLOUD_PROJECT con tu proyecto real, o usa --local.');
  initializeApp({ projectId: project, credential: applicationDefault() });
}
const db = getFirestore(), auth = getAuth();
if (local) {
  if (!(await db.doc('settings/payment').get()).exists) await db.doc('settings/payment').set({ businessName: 'Complejo de demostración', instructions: 'SOLO PRUEBA: no transfieras dinero. Usa una imagen ficticia para la evidencia.', yape: 'Número ficticio de demostración', plin: '', transfer: '' });
  // Cuentas ficticias únicamente dentro del emulador. No se crean en la nube.
  for (const [uid, email, fullName, role] of [
    ['admin-demo', 'admin@canchas.test', 'Administrador demo', 'admin'],
    ['cliente-demo', 'cliente@canchas.test', 'Deportista demo', 'client'],
  ]) {
    try { await auth.getUser(uid); } catch (e) {
      if (e.code !== 'auth/user-not-found') throw e;
      await auth.createUser({ uid, email, password: 'Canchas2026', displayName: fullName });
    }
    await db.doc(`users/${uid}`).set({ uid, email, fullName, phone: '', role, createdAtMs: Date.now() });
  }
}
if (process.argv.includes('--admin-uid')) {
  const uid = process.argv[process.argv.indexOf('--admin-uid') + 1];
  const user = await auth.getUser(uid);
  await db.doc(`users/${uid}`).set({ uid, email: user.email || '', fullName: user.displayName || 'Administrador', role: 'admin' }, { merge: true });
  console.log('Rol de administrador asignado al UID indicado.');
}
for (const [id, name, type, priceCents, location] of [
  ['el-gol', 'Cancha Sintética El Gol', 'Fútbol 7', 6000, 'Av. Mansiche, Trujillo'],
  ['los-leones', 'Complejo Deportivo Los Leones', 'Fútbol 5', 5000, 'Urbanización Primavera, Trujillo'],
  ['central', 'Cancha Central', 'Vóley', 4000, 'El Recreo, Trujillo'],
]) {
  const ref = db.doc(`courts/${id}`);
  if (!(await ref.get()).exists) await ref.set({ id, name, type, priceCents, location,
    description: 'Cancha de demostración para el proyecto del curso.', latitude: -8.1116, longitude: -79.0288, openingHour: 8, closingHour: 23, active: true, updatedAtMs: Date.now() });
}
console.log(local ? 'Datos locales listos. Cuentas: admin@canchas.test / cliente@canchas.test; contraseña: Canchas2026.' : 'Configuración completada en el proyecto indicado.');
process.exit(0);
