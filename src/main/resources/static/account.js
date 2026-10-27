'use strict';
(async()=>{
 const message=document.getElementById('accountMessage'),submit=document.getElementById('accountSubmit'),query=new URLSearchParams(location.search);
 const safeReturn=()=>{const raw=query.get('returnUrl')||'/leaderboard.html',allowed=/^\/(?:leaderboard(?:\.html)?|overview|regions(?:\/[a-z]+)?|countries(?:\/[a-z]{2})?|players|trends|archive|compare|watchlist|settings)(?:[/?#]|$)/;try{const target=new URL(raw,location.origin);return target.origin===location.origin&&allowed.test(target.pathname+target.search)?target.pathname+target.search+target.hash:'/leaderboard.html';}catch{return'/leaderboard.html';}};
 const returnUrl=safeReturn();const signupLink=document.querySelector('.switch a[href="/signup.html"]');if(signupLink)signupLink.href='/signup.html?returnUrl='+encodeURIComponent(returnUrl);
 if(query.has('error'))message.textContent='The username/email or password was not recognized.';
 if(query.has('created')){message.textContent='Account created. You can sign in now.';message.classList.add('success');}
 if(query.has('logout')){message.textContent='You have signed out.';message.classList.add('success');}
 try{
  const response=await fetch('/api/leaderboard/csrf');if(!response.ok)throw new Error('Unable to prepare this form. Please refresh.');const csrf=await response.json();
  const token=document.getElementById('csrfToken');if(token)token.value=csrf.token;submit.disabled=false;
  const signin=document.getElementById('signinForm');
  if(signin)signin.onsubmit=async event=>{
   event.preventDefault();submit.disabled=true;message.textContent='Signing in…';
   try{const response=await fetch('/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',[csrf.header]:csrf.token},body:new URLSearchParams(new FormData(signin))});const finalUrl=new URL(response.url,location.href);if(!response.ok||(finalUrl.pathname==='/signin.html'&&finalUrl.searchParams.has('error')))throw new Error('The username/email or password was not recognized.');location.assign(returnUrl);}
   catch(error){message.textContent=error.message;submit.disabled=false;}
  };
  const form=document.getElementById('signupForm');
  if(form)form.onsubmit=async event=>{
   event.preventDefault();submit.disabled=true;message.textContent='';
   const fields=new FormData(form),body=Object.fromEntries(fields.entries());
   try{const r=await fetch('/api/account/register',{method:'POST',headers:{'Content-Type':'application/json',[csrf.header]:csrf.token},body:JSON.stringify(body)});
    if(!r.ok){let data={};try{data=await r.json();}catch(ignored){}throw new Error(data.message||data.detail||(r.status===409?'That username or email is already registered.':r.status===403?'Your session expired. Refresh and try again.':'Please check your details and try again.'));}
    location.href='/signin.html?created=1&returnUrl='+encodeURIComponent(returnUrl);
   }catch(error){message.textContent=error.message;submit.disabled=false;}
  };
 }catch(error){message.textContent=error.message;}
})();
