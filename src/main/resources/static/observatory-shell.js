'use strict';
(()=>{
 const params=new URLSearchParams(location.search),currentView=params.get('view')||'world';
 const currentRegion=params.get('region')||'ap';let selectedAct=params.get('act')||'',currentAct='';
 const validRegions=new Set(['ap','eu','na','latam','br','kr']);
 function shellHref(view){
  const p=new URLSearchParams({view});
  if(view==='region')p.set('region',validRegions.has(currentRegion)?currentRegion:'ap');
  else {p.set('region','all');p.set('country','all');}
  if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);
  return '/leaderboard.html?'+p.toString();
 }
 function syncShellNavigation(latestCurrentAct=currentAct,activeAct=selectedAct){
  currentAct=latestCurrentAct||'';selectedAct=activeAct||'';
  document.querySelectorAll('[data-shell-view]').forEach(link=>{
   const view=link.dataset.shellView,active=view===currentView||(view==='region'&&['country'].includes(currentView));
   link.href=shellHref(view);if(active)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');
  });
  const archived=Boolean(selectedAct&&currentAct&&selectedAct!==currentAct);
  document.querySelectorAll('[data-shell-action="archive"]').forEach(button=>button.setAttribute('aria-pressed',String(archived)));
 }
 document.querySelectorAll('[data-shell-action="archive"]').forEach(button=>button.addEventListener('click',()=>{
  const filter=document.getElementById('actFilter');if(!filter)return;
  filter.scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'center'});
  filter.focus({preventScroll:true});
 }));
 globalThis.syncShellNavigation=syncShellNavigation;
 syncShellNavigation();
})();
