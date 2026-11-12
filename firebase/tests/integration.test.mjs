import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {initializeApp as adminApp} from 'firebase-admin/app';
import {getFirestore as adminDb} from 'firebase-admin/firestore';
import {getAuth as adminAuth} from 'firebase-admin/auth';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,connectAuthEmulator,signInWithEmailAndPassword} from 'firebase/auth';
import {getFirestore,connectFirestoreEmulator,doc,getDoc,setDoc,updateDoc,runTransaction,writeBatch} from 'firebase/firestore';
const projectId='demo-canchas-spark';
if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) throw Error('Solo emuladores');
adminApp({projectId}); const server=adminDb(); const apps=[];
const date=new Date(Date.now()+3*86400000).toISOString().slice(0,10);
const begin=hour=>Date.parse(`${date}T${String(hour).padStart(2,'0')}:00:00-05:00`);
const slot=(id,h,hold)=>({bookingId:id,date,hour:h,holdUntilMs:hold,blocked:false,reason:''});
let alice,bob,admin;
async function session(uid,role='client') {
  try { await adminAuth().deleteUser(uid); } catch(e) { if(e.code !== 'auth/user-not-found') throw e; }
  await adminAuth().createUser({uid,email:`${uid}@test.invalid`,password:'Password123'});
  await server.doc(`users/${uid}`).set({uid,email:`${uid}@test.invalid`,fullName:uid,phone:'',role});
  const app=initializeApp({projectId,apiKey:'demo-key'},uid); apps.push(app);
  const auth=getAuth(app); connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
  const db=getFirestore(app); connectFirestoreEmulator(db,'127.0.0.1',8080);
  await signInWithEmailAndPassword(auth,`${uid}@test.invalid`,'Password123');
  return {db,uid};
}
function booking(who,id,h) {
 const now=Date.now(); return {id,userId:who.uid,userName:who.uid,courtId:'test',courtName:'Cancha test',date,hour:h,startMs:begin(h),totalCents:6000,status:'Pendiente de pago',createdAtMs:now,updatedAtMs:now,holdUntilMs:now+900000,paid:false,refunded:false,refundPending:false,paymentMethod:'',paymentReference:'',paymentKey:'',reviewNote:'',reviewedBy:'',cancellationReason:'',refundedBy:''};
}
async function reserve(who,id,h,extra={}) {
 const b=booking(who,id,h); Object.assign(b,extra);
 await runTransaction(who.db,async tx=>{
   const ref=doc(who.db,`courts/test/slots/${date}_${h}`); const old=await tx.get(ref);
   if(old.exists() && old.data().holdUntilMs>Date.now()) throw Error('Ocupado');
   tx.set(doc(who.db,`bookings/${id}`),b); tx.set(ref,slot(id,h,b.holdUntilMs));
 }); return b;
}
async function pay(who,id,reference='ABC123') {
 const ref=doc(who.db,`bookings/${id}`), b=(await getDoc(ref)).data(), now=Date.now();
 const batch=writeBatch(who.db), key=`Yape_${reference}`;
 batch.update(ref,{status:'En revisión',paymentMethod:'Yape',paymentReference:reference,paymentKey:key,holdUntilMs:now+86400000,updatedAtMs:now});
 batch.set(doc(who.db,`courts/test/slots/${date}_${b.hour}`),slot(id,b.hour,now+86400000));
 batch.set(doc(who.db,`paymentProofs/${id}`),{userId:who.uid,imageBase64:'ficticioParaTest',mimeType:'image/jpeg',submittedAtMs:now});
 batch.set(doc(who.db,`paymentReferences/${key}`),{userId:who.uid,bookingId:id,method:'Yape',reference});
 await batch.commit();
}
before(async()=>{
 for(const b of (await server.collection('bookings').where('courtId','==','test').get()).docs) { await server.doc(`paymentProofs/${b.id}`).delete(); await b.ref.delete(); }
 await server.doc('paymentReferences/Yape_ABC123').delete();
 await server.recursiveDelete(server.doc('courts/test'));
 await server.doc('courts/admin-new').delete();
 alice=await session('spark-alice'); bob=await session('spark-bob'); admin=await session('spark-admin','admin');
 await server.doc('courts/test').set({id:'test',name:'Cancha test',type:'Fútbol',location:'Trujillo',description:'Demo',priceCents:6000,openingHour:8,closingHour:23,active:true,latitude:-8.11,longitude:-79.02});
});
after(async()=>{await Promise.all(apps.map(deleteApp));});
test('cliente no puede elevar su rol ni modificar precios',async()=>{
 await assert.rejects(updateDoc(doc(alice.db,`users/${alice.uid}`),{role:'admin'}));
 await assert.rejects(updateDoc(doc(alice.db,'courts/test'),{priceCents:1}));
});
test('reglas rechazan reserva sin bloqueo atómico y precio manipulado',async()=>{
 await assert.rejects(setDoc(doc(alice.db,'bookings/no-slot'),booking(alice,'no-slot',8)));
 await assert.rejects(reserve(alice,'cheap',8,{totalCents:1}));
});
test('dos clientes concurrentes: solo uno obtiene el horario',async()=>{
 const results=await Promise.allSettled([reserve(alice,'race-a',9),reserve(bob,'race-b',9)]);
 assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
});
test('una escritura maliciosa no reemplaza un horario ocupado',async()=>{
 await reserve(alice,'occupied',10);
 const b=booking(bob,'stolen',10), batch=writeBatch(bob.db);
 batch.set(doc(bob.db,'bookings/stolen'),b); batch.set(doc(bob.db,`courts/test/slots/${date}_10`),slot(b.id,10,b.holdUntilMs));
 await assert.rejects(batch.commit());
});
test('privacidad: otros clientes no leen reservas ni comprobantes',async()=>{
 await reserve(alice,'private',11); await pay(alice,'private');
 await assert.rejects(getDoc(doc(bob.db,'bookings/private')));
 await assert.rejects(getDoc(doc(bob.db,'paymentProofs/private')));
});
test('solo administrador confirma; cancelación y devolución mantienen estado consistente',async()=>{
 const ref=doc(admin.db,'bookings/private');
 await assert.rejects(updateDoc(doc(alice.db,'bookings/private'),{status:'Confirmada',paid:true,updatedAtMs:Date.now()}));
 let batch=writeBatch(admin.db);
 batch.update(ref,{status:'Confirmada',paid:true,reviewNote:'Ingreso verificado',reviewedBy:admin.uid,updatedAtMs:Date.now()});
 batch.set(doc(admin.db,`courts/test/slots/${date}_11`),slot('private',11,9007199254740991)); await batch.commit();
 batch=writeBatch(alice.db); batch.update(doc(alice.db,'bookings/private'),{status:'Cancelada',refundPending:true,cancellationReason:'Solicitud del cliente',updatedAtMs:Date.now()});
 batch.set(doc(alice.db,`courts/test/slots/${date}_11`),slot('',11,0)); await batch.commit();
 await assert.rejects(updateDoc(doc(alice.db,'bookings/private'),{refunded:true,refundPending:false,refundedBy:alice.uid,updatedAtMs:Date.now()}));
 await updateDoc(ref,{refunded:true,refundPending:false,refundedBy:admin.uid,updatedAtMs:Date.now()});
 assert.equal((await getDoc(ref)).data().refunded,true);
});
test('referencia de pago no se puede reutilizar',async()=>{
 await reserve(alice,'duplicate',12); await assert.rejects(pay(alice,'duplicate'));
});
test('horario vencido se reutiliza sin modificar reserva ajena',async()=>{
 const b=booking(alice,'expired',13); b.holdUntilMs=Date.now()-1000;
 await server.doc('bookings/expired').set(b); await server.doc(`courts/test/slots/${date}_13`).set(slot(b.id,13,b.holdUntilMs));
 await reserve(bob,'replacement',13);
 const batch=writeBatch(alice.db); batch.update(doc(alice.db,'bookings/expired'),{status:'Cancelada',cancellationReason:'Cancelar vencida',refundPending:false,updatedAtMs:Date.now()});
 batch.set(doc(alice.db,`courts/test/slots/${date}_13`),slot('',13,0));
 await assert.rejects(batch.commit());
 assert.equal((await server.doc(`courts/test/slots/${date}_13`).get()).data().bookingId,'replacement');
});
test('no se confirma reserva vencida ni se sube comprobante aislado',async()=>{
 await assert.rejects(updateDoc(doc(admin.db,'bookings/expired'),{status:'Confirmada',paid:true,reviewedBy:admin.uid,reviewNote:'Tarde',updatedAtMs:Date.now()}));
 await assert.rejects(setDoc(doc(alice.db,'paymentProofs/isolated'),{userId:alice.uid,imageBase64:'12345678',mimeType:'image/jpeg',submittedAtMs:Date.now()}));
});
test('administrador crea, actualiza y desactiva canchas; cliente no ve inactivas',async()=>{
 const ref=doc(admin.db,'courts/admin-new');
 await runTransaction(admin.db,async tx=>{
   await tx.get(ref);
   tx.set(ref,{id:'admin-new',name:'Nueva cancha',type:'Vóley',location:'Trujillo',description:'Demo',priceCents:4000,openingHour:8,closingHour:22,active:true,latitude:-8.11,longitude:-79.02});
 });
 await updateDoc(ref,{priceCents:4500});
 assert.equal((await getDoc(doc(alice.db,'courts/admin-new'))).data().priceCents,4500);
 await updateDoc(ref,{active:false});
 await assert.rejects(getDoc(doc(alice.db,'courts/admin-new')));
 assert.equal((await getDoc(ref)).data().active,false);
});
test('bloqueo administrativo impide reservar y se puede habilitar de nuevo',async()=>{
 const ref=doc(admin.db,`courts/test/slots/${date}_14`);
 await setDoc(ref,{...slot('',14,9007199254740991),blocked:true,reason:'Mantenimiento'});
 await assert.rejects(reserve(alice,'blocked',14));
 await setDoc(ref,slot('',14,0));
 await reserve(alice,'unblocked',14);
});
test('perfil propio editable; configuración de cobros solo administrador',async()=>{
 await updateDoc(doc(alice.db,`users/${alice.uid}`),{phone:'999888777'});
 const config={businessName:'Demo',instructions:'No pagar',yape:'',plin:'',transfer:''};
 await assert.rejects(setDoc(doc(alice.db,'settings/payment'),config));
 await setDoc(doc(admin.db,'settings/payment'),config);
 assert.equal((await getDoc(doc(alice.db,'settings/payment'))).data().businessName,'Demo');
});
