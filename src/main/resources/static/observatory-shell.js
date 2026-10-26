'use strict';
(()=>{
 const params=new URLSearchParams(location.search),currentView=params.get('view')||(location.pathname==='/leaderboard'?'global':'world');
 const currentRegion=params.get('region')||'ap';let selectedAct=params.get('act')||'',currentAct='';
 const validRegions=new Set(['ap','eu','na','latam','br','kr']);
 function shellHref(view){
  const p=new URLSearchParams({view});
  if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);
  if(view==='global')return '/leaderboard'+(p.has('act')?'?act='+encodeURIComponent(p.get('act')):'');
  return (view==='world'?'/overview':view==='region'?'/regions/'+encodeURIComponent(validRegions.has(currentRegion)?currentRegion:'ap'):view==='countries'?'/countries':'/leaderboard.html')+(p.has('act')?'?act='+encodeURIComponent(p.get('act')):'');
 }
 function syncShellNavigation(latestCurrentAct=currentAct,activeAct=selectedAct){
  currentAct=latestCurrentAct||'';selectedAct=activeAct||'';
  document.querySelectorAll('[data-shell-view]').forEach(link=>{
   const view=link.dataset.shellView,active=view===currentView||(view==='countries'&&currentView==='country');
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
 document.getElementById('historicalTrendDisclosure')?.addEventListener('toggle',event=>{if(event.currentTarget.open)requestAnimationFrame(()=>window.dispatchEvent(new Event('resize')))});
})();
