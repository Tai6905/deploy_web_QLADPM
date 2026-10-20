import {test} from 'node:test';
import assert from 'node:assert/strict';
import * as vue from 'vue';
import {loadSource} from './load-source.mjs';

function checkout(api) {
  const events=[];
  const component=loadSource('src/views/CheckoutPage.vue',{vueSetup:true,imports:{
    vue:{...vue,onMounted(){},onUnmounted(){},watch(){}},
    'lucide-vue-next':{},'../api/shopflowApi':{shopflowApi:api},'../data':{money:String},
    '../auth/session':{dataSource:'api',currentUser:vue.ref({userId:1,displayName:'Test customer'})},
  }});
  const app=component.default.setup({cartItems:[{product:{id:1,price:25000,name:'Cup',stock:10},quantity:2}],cartTotal:50000},{expose(){},emit:(...args)=>events.push(args)});
  Object.assign(app.address,{receiverName:'Receiver',phone:'0901234567',addressLine:'12 Test street',city:'Test city'});
  app.loading.value=false;
  return {app,events};
}
test('checkout ignores a slower quote for an older voucher',async()=>{
  const pending=[];
  const {app}=checkout({quoteCheckout:()=>new Promise(resolve=>pending.push(resolve))});
  const first=app.refreshQuote();app.appliedVoucher.value='NEW';const second=app.refreshQuote();
  pending[1]({subtotalAmount:50000,discountAmount:5000,totalAmount:45000,voucherCode:'NEW'});await second;
  pending[0]({subtotalAmount:50000,discountAmount:0,totalAmount:50000,voucherCode:null});await first;
  assert.equal(app.quote.value.totalAmount,45000);assert.equal(app.quote.value.voucherCode,'NEW');
});
test('checkout blocks duplicate clicks and retries the same key without auto-paying',async()=>{
  let resolveFirst;const calls=[];let paymentCalls=0;
  const {app,events}=checkout({placeCheckout:input=>{calls.push(input);return calls.length===1?new Promise((_,reject)=>{resolveFirst=reject;}):Promise.resolve({id:7,orderRef:'ref',paymentMethod:'CARD',status:'PENDING_PAYMENT'});},quoteCheckout:async()=>({subtotalAmount:50000,discountAmount:0,totalAmount:50000}),simulatePayment:()=>paymentCalls++});
  app.quote.value={subtotalAmount:50000,discountAmount:0,totalAmount:50000};app.payment.value='CARD';app.remember.value=false;
  const first=app.place();await app.place();assert.equal(calls.length,1);
  resolveFirst(new Error('Network timeout'));await first;assert.equal(events.length,0);
  await app.place();assert.equal(calls.length,2);assert.equal(calls[0].checkoutKey,calls[1].checkoutKey);
  assert.equal(calls[1].saveAddress,false);assert.equal(calls[1].expectedTotal,50000);
  assert.equal(paymentCalls,0);assert.equal(events[0][0],'clear-cart');
});
test('selecting a saved address prefills shipping and a new address clears previous contact data',()=>{
  const {app}=checkout({});
  app.select({id:8,receiverName:'Saved recipient',phone:'0987654321',addressLine:'Saved street',city:'City',isDefault:true});
  assert.equal(app.address.phone,'0987654321');assert.equal(app.selected.value,8);assert.equal(app.editor.value,false);
  app.newAddress();assert.equal(app.selected.value,null);assert.equal(app.address.phone,'');assert.equal(app.address.addressLine,'');
});
