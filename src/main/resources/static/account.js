'use strict';
(async()=>{
 const message=document.getElementById('accountMessage'),submit=document.getElementById('accountSubmit'),query=new URLSearchParams(location.search);
 if(query.has('error'))message.textContent='The username/email or password was not recognized.';
 if(query.has('created')){message.textContent='Account created. You can sign in now.';message.classList.add('success');}
 if(query.has('logout')){message.textContent='You have signed out.';message.classList.add('success');}
 try{
  const response=await fetch('/api/leaderboard/csrf');if(!response.ok)throw new Error('Unable to prepare this form. Please refresh.');const csrf=await response.json();
  const token=document.getElementById('csrfToken');if(token)token.value=csrf.token;submit.disabled=false;
  const form=document.getElementById('signupForm');
  if(form)form.onsubmit=async event=>{
   event.preventDefault();submit.disabled=true;message.textContent='';
   const fields=new FormData(form),body=Object.fromEntries(fields.entries());
   try{const r=await fetch('/api/account/register',{method:'POST',headers:{'Content-Type':'application/json',[csrf.header]:csrf.token},body:JSON.stringify(body)});
    if(!r.ok){let data={};try{data=await r.json();}catch(ignored){}throw new Error(data.message||data.detail||(r.status===409?'That username or email is already registered.':r.status===403?'Your session expired. Refresh and try again.':'Please check your details and try again.'));}
    location.href='/signin.html?created=1';
   }catch(error){message.textContent=error.message;submit.disabled=false;}
  };
 }catch(error){message.textContent=error.message;}
})();
