'use strict';
const $=id=>document.getElementById(id);
const esc=value=>String(value??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const time=v=>v?new Date(v).toLocaleString():'—';
const fmt=v=>v==null?'—':Number(v).toLocaleString();
const params=new URLSearchParams(location.search),view=params.get('view')||(location.pathname==='/leaderboard'?'global':location.pathname==='/players'?'profile':location.pathname==='/trends'?'trends':location.pathname==='/archive'?'archive':location.pathname==='/compare'?'compare':location.pathname==='/watchlist'?'watchlist':location.pathname==='/settings'?'settings':'world');
let selectedAct=params.get('act')||'';let currentAct='';
const playerPageSize=view==='country'||view==='global'?10:20;
const playerSorts=new Set(['globalRank','regionalRank','name','country','tier','rr','rrChange','updated']);
const requestedProfilePlayer=params.get('player')||'',requestedProfileRegion=params.get('region')||'ap';
let region=params.get('region')||'all',country=params.get('country')||'all',page=Math.max(1,Math.floor(Number(params.get('page')||1))),countryPage=1,search=params.get('q')||params.get('search')||'',generation=0,trendMinutes=60,direction='overall',sortKey=playerSorts.has(params.get('sort'))?params.get('sort'):'globalRank',sortDirection=params.get('direction')==='desc'?'desc':'asc';
let previewCountry=null,previewGeneration=0,worldTrendCountry=null,moverCountry='all',countryMode='current',activityMinutes=60,activityVersion=0;
let data=null,countryNames={},regionNames={},countryPages=1,playerPages=1,expandedScope='';
let subregion='all';
// Riot Challengers Pacific territories, grouped for country browsing, not queue restrictions.
const pacificSubregions={sa:{name:'SA · South Asia',countries:'in pk mv np lk bt bd'},sea:{name:'SEA · Southeast Asia',countries:'id th vn ph my sg hk tw mn mm la kh bn mo tl'},jp:{name:'Japan',countries:'jp'},kr:{name:'South Korea',countries:'kr'},oce:{name:'OCE · Oceania',countries:'au nz fj fm pg ws to ki sb pw tv nr mh vu'}};
const emeaSubregions={ne:{name:'NORTH//EAST',countries:'gb ie se dk no fi is pl cz sk ee lv lt al ba bg xk me mk rs hr hu ro gr cy si il am az by kz kg md ru tj uz tm ua ge'},fr:{name:'France · Benelux',countries:'fr mc be nl lu'},es:{name:'Spain · Portugal · Italy',countries:'es pt it ad mt sm va'},dach:{name:'DACH',countries:'de at ch li'},tr:{name:'Türkiye',countries:'tr'},mena:{name:'MENA',countries:'sa kw ae qa bh om eg lb iq ps jo dz ly ma tn'}};
const americasSubregions={na:{name:'NA · North America',countries:'us ca'},lan:{name:'LAN · LATAM North',countries:'co ec mx ve gt bz hn sv ni cr pa ag aw cw bs bb bq dm gd gp gy ht ky tc vi vg jm mq pr do kn vc lc tt'},las:{name:'LAS · LATAM South',countries:'ar bo cl py pe uy'},br:{name:'BR · Brazil',countries:'br'}};
const subregionGroups=()=>region==='eu'?emeaSubregions:['na','latam','br'].includes(region)?americasSubregions:pacificSubregions;
const matchesSubregion=c=>subregion==='all'||(subregion==='other'?!Object.values(subregionGroups()).some(g=>g.countries.split(' ').includes(c)):subregionGroups()[subregion]?.countries.split(' ').includes(c));
let regionSort={key:'players',dir:'desc'},countrySort={key:'players',dir:'desc'};
const codes={ap:'AP',eu:'EU',na:'NA',latam:'LA',br:'BR',kr:'KR'};
const countryName=c=>c==='kr'?'South Korea':countryNames[c||'unknown']||c||'Unknown country';
const defaultRegionNames={ap:'Asia-Pacific',eu:'Europe',na:'North America',latam:'Latin America',br:'Brazil',kr:'South Korea'};const regionName=c=>regionNames?.[c]||defaultRegionNames[c]||c;
const regionSymbol=c=>`<img class="region-symbol region-photo" src="/scenery/${esc(c)}.jpg" alt="" aria-hidden="true" loading="lazy">`;
const playerIdentity=value=>{const name=String(value||'Unknown player'),i=name.lastIndexOf('#');return `<span class="player-identity"><span class="player-initial" aria-hidden="true">${esc(name.slice(0,1).toUpperCase())}</span><span class="riot-name">${esc(i<0?name:name.slice(0,i))}</span>${i<0?'':`<span class="riot-tag">${esc(name.slice(i))}</span>`}</span>`;};
const flag=c=>!c||c==='unknown'?'<span class="unknown-symbol" aria-hidden="true">?</span>':`<img class="flag" src="/flags/${esc(c)}.svg" alt="" loading="lazy">`;
const countryLabel=c=>flag(c)+esc(countryName(c));
const globalPlayerCard=p=>`<details class="global-player-card"><summary><span class="global-card-rank">${p.global_rank==null?'Unranked':'#'+fmt(p.global_rank)}</span><span class="global-card-identity">${playerIdentity(p.display_name)}<small>${countryLabel(p.country)}</small></span><span class="global-card-rating">${fmt(p.rr)} <small>RR</small></span></summary><div class="global-card-details"><div><span>Global rank</span><strong>${rank(p.global_rank,p)}</strong></div><div><span>Regional rank</span><strong>${rank(p.regional_rank,p)}</strong></div><div><span>Rank tier</span><strong>${tierBadge(p.tier)}${p.stats_ambiguous?'*':''}</strong></div><div><span>Rating change</span><strong>${delta(p.rr_change)}</strong></div><div><span>Stats as of</span><strong>${esc(time(p.source_updated_at))}${p.carried_forward?' · older':''}</strong></div><button type="button" class="global-card-open" data-player="${esc(p.player_key)}" data-region="${esc(p.region)}">Open player profile</button></div></details>`;
const pageUrl=(kind,r='all',c='all')=>{const p=new URLSearchParams();if(kind==='world'){if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);return '/overview'+(p.size?'?'+p:'');}if(kind==='region'&&r!=='all'){if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);return '/regions/'+encodeURIComponent(r)+(p.size?'?'+p:'');}if(kind==='countries'){if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);return '/countries'+(p.size?'?'+p:'');}if(kind==='country'&&r!=='all'&&c!=='all'){p.set('region',r);if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);return '/countries/'+encodeURIComponent(c)+'?'+p;}if(kind==='global'){if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);return '/leaderboard'+(p.size?'?'+p:'');}p.set('view',kind);p.set('region',r);p.set('country',c);if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);return '/leaderboard.html?'+p;};
const playerProfileUrl=(player,shard)=>{const p=new URLSearchParams({player,region:shard});if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);return '/players?'+p;};
function syncGlobalUrl(push=false){if(view!=='global')return;const p=new URLSearchParams();if(selectedAct&&selectedAct!==currentAct)p.set('act',selectedAct);if(search.trim())p.set('q',search.trim());if(page>1)p.set('page',String(page));if(sortKey!=='globalRank')p.set('sort',sortKey);if(sortDirection!=='asc')p.set('direction',sortDirection);const target='/leaderboard'+(p.size?'?'+p:'');if(location.pathname+location.search!==target)history[push?'pushState':'replaceState']({global:true},'',target);}
function updateShellBreadcrumbs(){
 const host=$('breadcrumbs');if(!host)return;
 const items=[];
 if(view==='world')items.push('<span class="breadcrumb-current" aria-current="page">Overview</span>');
 else items.push(`<a class="breadcrumb-link" href="${esc(pageUrl('world'))}">Overview</a>`);
 if(view==='region'&&region!=='all')items.push(`<span class="breadcrumb-current" aria-current="page">${esc(regionName(region))}</span>`);
 if(view==='country'&&region!=='all'){
  items.push(`<a class="breadcrumb-link" href="${esc(pageUrl('region',region))}">${esc(regionName(region))}</a>`);
  if(country!=='all')items.push(`<span class="breadcrumb-current" aria-current="page">${esc(countryName(country))}</span>`);
 }
 if(view==='global')items.push('<span class="breadcrumb-current" aria-current="page">Global leaderboard</span>');
 if(view==='compare')items.push('<span class="breadcrumb-current" aria-current="page">Compare</span>');
 const selected=$('actFilter')?.selectedOptions?.[0]?.textContent?.replace(/\s*·\s*Live$/, '')||'';
 if(selectedAct&&selectedAct!==currentAct&&selected)items.push(`<span class="breadcrumb-current" aria-current="page">${esc(selected)} archive</span>`);
 host.innerHTML=items.join('<span class="subtle" aria-hidden="true">›</span>');
 globalThis.syncShellNavigation?.(currentAct,selectedAct);
}
const delta=v=>v==null?'—':`<span class="${v>=0?'positive':'negative'}">${v>0?'+':''}${fmt(v)}</span>`;
const rank=(v,p)=>v==null?'—':`${fmt(v)}${p.rank_ambiguous||p.stats_ambiguous?'*':''}`;
async function api(path,options){if(selectedAct&&path!=='acts'){const [endpoint,query='']=path.split('?',2),p=new URLSearchParams(query);if(!p.has('act'))p.set('act',selectedAct);path=endpoint+'?'+p;}const r=await fetch('/api/leaderboard/'+path,options);if(!r.ok)throw new Error(`Unable to load leaderboard (${r.status}).`);return r.json();}
async function initializeActFilter(){
 const response=await fetch('/api/leaderboard/acts');if(!response.ok)throw new Error('Unable to load available acts.');const acts=await response.json();
 const current=acts.find(a=>a.current_act);currentAct=current?.act||'';if(view==='archive'&&(!selectedAct||selectedAct===currentAct||!acts.some(a=>a.act===selectedAct&&!a.current_act)))selectedAct=acts.find(a=>!a.current_act)?.act||currentAct;if(!selectedAct||!acts.some(a=>a.act===selectedAct))selectedAct=currentAct;globalThis.syncShellNavigation?.(currentAct,selectedAct);
 $('actFilter').dataset.acts=JSON.stringify(acts);$('actFilter').innerHTML=acts.map(a=>`<option value="${esc(a.act)}">${esc(a.label)}${a.current_act?' · Live':''}</option>`).join('');$('actFilter').value=selectedAct;
 $('masthead').querySelector('.brand-eyebrow').href=pageUrl('world');const globalLink=$('regionSection').querySelector('a.page-link');if(globalLink)globalLink.href=pageUrl('global');
$('actFilter').onchange=()=>{let next=$('actFilter').value;if(view==='archive'&&next===currentAct)next=(JSON.parse($('actFilter').dataset.acts||'[]').find(a=>!a.current_act)?.act)||currentAct;const p=new URLSearchParams(location.search);if(next===currentAct)p.delete('act');else p.set('act',next);if(view==='global')p.delete('page');location.href=location.pathname+(p.toString()?'?'+p:'');};
}
const tierBadge=name=>{const normalized=String(name).replace(/s$/,'');const slug={'Radiant':'radiant','Immortal 1':'immortal-1','Immortal 2':'immortal-2','Immortal 3':'immortal-3','Immortal':'immortal-3'}[normalized];return slug?`<img class="rank-icon" src="/ranks/${slug}.png" alt="${esc(name)}" title="${esc(normalized==='Immortal'?'Immortal · all tiers':name)}" width="28" height="28">`:esc(name);};
const rankLabel=label=>String(label).split(/(Immortal [123]|Immortal|Radiants?)/g).map(t=>/^(Immortal(?: [123])?|Radiants?)$/.test(t)?tierBadge(t):esc(t)).join('');
const landmarks={eu:'Eiffel Tower · Paris',ap:'Taj Mahal · Agra',na:'Statue of Liberty · New York',br:'Christ the Redeemer · Rio de Janeiro',kr:'Gyeongbokgung Palace · Seoul',latam:'Machu Picchu · Peru'};
const numericColumns=['players','radiant','immortal3','immortal2','immortal1'];
function sorted(rows,sort,nameKey){return [...rows].sort((a,b)=>{
 let x=sort.key===nameKey?(nameKey==='country'?countryName(a.country):regionName(a.region)):a[sort.key],y=sort.key===nameKey?(nameKey==='country'?countryName(b.country):regionName(b.region)):b[sort.key];
 if(x==null||y==null)return x==null?(y==null?0:1):-1;
 const diff=typeof x==='string'?x.localeCompare(y):Number(x)-Number(y);
 return (sort.dir==='asc'?diff:-diff)||String(a[nameKey]).localeCompare(String(b[nameKey]));
});}
const columnHelp={active:'Distinct players with observed RR, win-count or tier changes. A lower bound on active players.',wins_added:'Recorded wins added within comparable source observations, not all matches played.',losses_added:'Estimated: each RR decrease between consecutive usable source observations counts as one loss, regardless of decrease size. Not a verified match count.',rr_positive:'Distinct players with a positive net RR change across comparable observations in this window.',rr_negative:'Distinct players with a negative net RR change across comparable observations in this window.',near_radiant:'Immortal players 0–50 RR below the observed regional Radiant cutoff.',radiant_boundary:'Radiant players less than 50 RR above the observed regional cutoff.',radiant_top50:'Highest-rated half of current, unflagged Radiant players in this region.',radiant_top10:'Highest-rated tenth of current, unflagged Radiant players in this region.'};
function headings(columns,sort,allowFirst,plainLabels=false){return '<tr>'+columns.map(([key,label],i)=>`<th title="${esc(columnHelp[key]||'')}" aria-sort="${sort.key===key?(sort.dir==='asc'?'ascending':'descending'):'none'}">${i===0&&!allowFirst?esc(label):`<button class="sort-button" data-sort="${key}" aria-label="Sort ${esc(label)} ${sort.key===key&&sort.dir==='desc'?'ascending':'descending'}">${plainLabels?esc(label):rankLabel(label)}<span aria-hidden="true">${sort.key===key?(sort.dir==='asc'?'↑':'↓'):'⇅'}</span></button>`}</th>`).join('')+'</tr>';}
function bandInfo(countries){const t=countries[0];if(!t||t.radiant_threshold==null)return null;return [[0,100],[100,300],[300,Number(t.cutoff)],[Number(t.cutoff),Number(t.midpoint)],[Number(t.midpoint),Number(t.highpoint)],[Number(t.highpoint),null]];}
const bandLabel=b=>b[1]==null?`${b[0]}+ RR`:b[0]>=b[1]?'Empty band':`${b[0]}–<${b[1]} RR`;
const raceColumns=['near_radiant','radiant_boundary','radiant_top50','radiant_top10'];
function estimatedCountryCounts(rows,shard=region,evidence={}){return CountryModel.estimate(rows,shard,evidence).counts;}
function estimatedPlayers(rows){return new Map([...estimatedCountryCounts(rows)].map(([c,r])=>[c,r.estimated_players]));}
function countryRow(c,pinned=false){const compact=view==='world',archive=data.status.archiveOnly,keys=archive?['players','radiant','immortal_all','immortal','immortal3','immortal2','immortal1']:compact?numericColumns:[...numericColumns,...raceColumns];return `<tr${pinned?' class="pinned-unknown"':''}><td><button class="country-link" aria-expanded="${previewCountry===c.country}" data-country="${esc(c.country)}">${countryLabel(c.country)}</button></td>${keys.map(k=>`<td>${fmt(c[k]||0)}</td>${!archive&&numericColumns.includes(k)&&$('estimateToggle').checked?`<td class="estimate-value" title="Model allocation, not an observed country label">${pinned?(k==='players'?'Source only':'—'):estimatedCounts.has(c.country)?'≈ '+fmt(estimatedCounts.get(c.country)['estimated_'+k]):'—'}</td>`:''}`).join('')}${compact||archive?'':`<td>${c.median_rr==null?'—':fmt(c.median_rr)}</td>`}</tr>`;}
let estimates=new Map(),estimatedCounts=new Map(),overviewRegionMinutes=60,overviewCountryMinutes=60,overviewActivityCache=new Map(),overviewActivityVersions={region:0,country:0},overviewSearchTimer;const overviewSearchRequest=ObservatoryLifecycle.createLatestRequest();
function renderTables(){
 const {regions,countries,result}=data;
 $('playerSection').dataset.view=view;$('globalPlayerCards').hidden=view!=='global';
 const archiveTierColumns=['players','radiant','immortal_all','immortal','immortal3','immortal2','immortal1'];
 const archiveTierLabels={players:'Players',radiant:'Radiant',immortal_all:'Immortal total',immortal:'Immortal combined only',immortal3:'Reported Immortal 3',immortal2:'Reported Immortal 2',immortal1:'Reported Immortal 1'};
 const tierColumns=data.status.archiveOnly?archiveTierColumns:numericColumns,cardTierColumns=data.status.archiveOnly?['players','radiant','immortal_all']:tierColumns;
 const carousel=$('regionShortcuts'),carouselPosition=carousel.scrollLeft,regionOrder=['ap','eu','na','latam','br','kr'],regionByCode=new Map(regions.map(r=>[r.region,r]));
 $('regionHead').innerHTML=headings([['region','Region'],...tierColumns.map(k=>[k,data.status.archiveOnly?archiveTierLabels[k]:({players:'Players',radiant:'Radiant',immortal3:'Immortal 3',immortal2:'Immortal 2',immortal1:'Immortal 1'}[k])])],regionSort,false,data.status.archiveOnly);
 $('regions').innerHTML=sorted(regions,regionSort,'region').map(r=>`<tr><td><button class="region-link" data-region="${esc(r.region)}" aria-expanded="${region===r.region}">${regionSymbol(r.region)}${esc(regionName(r.region))}</button></td>${tierColumns.map(k=>`<td>${fmt(r[k])}</td>`).join('')}</tr>`).join('')||'<tr><td colspan="6">No complete regional snapshot is available yet.</td></tr>';
 const completeCount=regionOrder.filter(code=>regionByCode.has(code)).length,latest=data.status.latestSnapshot?.[0]?.completed_at;
 $('regionAvailability').textContent=`${completeCount} of 6 regions have ${data.status.archiveOnly?'archived standings':'a complete regional snapshot'}${latest?' · latest complete snapshot '+time(latest):''}.`;
 const observed=sorted(regions,{key:'players',dir:'desc'},'region').map(r=>r.region),ordered=[...observed,...regionOrder.filter(code=>!regionByCode.has(code))];
 $('regionShortcuts').innerHTML=ordered.map(code=>{const r=regionByCode.get(code);if(!r){const label=data.status.archiveOnly?'No completed standings for this Act.':'No complete regional snapshot is available yet.';return `<a class="region-shortcut region-shortcut-unavailable" href="${esc(pageUrl('region',code))}" aria-label="Explore ${esc(regionName(code))} · snapshot unavailable" style="--region-scene:url('/scenery/${esc(code)}.jpg')"><span class="region-unavailable-card"><span class="card-heading">${regionSymbol(code)}${esc(regionName(code))}<b aria-hidden="true">↗</b></span><span class="card-landmark">Snapshot unavailable</span><span class="subtle">${esc(label)}</span><span class="card-back-link">Open region →</span></span></a>`;}const playerLabel=r.top_rank===1?'#1 observed regional player':r.top_rank?'Highest available regional rank · #'+fmt(r.top_rank):'Leading observed player',stats=cardTierColumns.map(k=>`<span><small>${rankLabel(data.status.archiveOnly?archiveTierLabels[k]:({players:'Players',radiant:'Radiant',immortal3:'Immortal 3',immortal2:'Immortal 2',immortal1:'Immortal 1'}[k]))}</small><strong>${fmt(r[k])}</strong></span>`).join('');return `<a class="region-shortcut" href="${esc(pageUrl('region',r.region))}" aria-label="Explore ${esc(regionName(r.region))} regional standings" style="--region-scene:url('/scenery/${esc(r.region)}.jpg')"><span class="card-flipper"><span class="card-face card-front"><span class="card-heading">${regionSymbol(r.region)}${esc(regionName(r.region))}<b aria-hidden="true">↗</b></span><span class="card-landmark">${esc(landmarks[r.region]||regionName(r.region))}</span><span class="card-player"><small>${esc(playerLabel)}</small><span class="featured-player"><span class="featured-name">${esc(r.top_player||'Not available yet')}</span>${r.top_rr==null?'':`<span class="featured-rr">${fmt(r.top_rr)} <span>RR</span></span>`}</span></span><span class="card-prompt" aria-hidden="true">Open region · hover or focus for standings</span></span><span class="card-face card-back"><span class="card-heading" aria-hidden="true">${regionSymbol(r.region)}${esc(regionName(r.region))}<b>↗</b></span><span class="card-stats">${stats}</span><span class="card-back-link" aria-hidden="true">Open region →</span></span></span></a>`;}).join('');
 carousel.scrollLeft=carouselPosition;
 const modelResult=data.status.archiveOnly?{counts:new Map(),recoveryTotal:0,remainingUnknown:0,unresolved:{players:0},access:{appliedCount:0}}:CountryModel.estimate(countries,region,data.modelEvidence||{});estimatedCounts=modelResult.counts;estimates=new Map([...estimatedCounts].map(([c,r])=>[c,r.estimated_players]));$('estimateNote').classList.toggle('hidden',data.status.archiveOnly||!$('estimateToggle').checked);$('estimateMethod').classList.toggle('hidden',data.status.archiveOnly||!$('estimateToggle').checked);
 $('estimateValidation').textContent=`${fmt(modelResult.recoveryTotal)} reported countries recovered first; ${fmt(modelResult.remainingUnknown)} remain unknown. Upper groups use country group count × (3 × Radiant + 2 × Immortal 3 + all Immortal 2). Immortal 1 uses country population × (1 + median RR / 150), capped at a 2× median multiplier. All Immortal 2 accounts use the upper rule, with no RR split. ${fmt(modelResult.unresolved.players)} accounts lack sufficient allocation evidence. Network-access proxy adjustment applies to ${fmt(modelResult.access?.appliedCount||0)} countries in this view; unmeasured or flagged data stays neutral. HTTPS proxy RTT is not in-game ping. This is an explicit weighting policy, not measured country accuracy.`;


 const countryColumns=data.status.archiveOnly?[['country','Country'],...archiveTierColumns.map(k=>[k,archiveTierLabels[k]])]:[['country','Country'],['players','Players'],['radiant','Radiant'],['immortal3','Immortal 3'],['immortal2','Immortal 2'],['immortal1','Immortal 1'],['near_radiant','Near Radiant'],['radiant_boundary','Radiant · near cutoff'],['radiant_top50','Top 50% Radiants'],['radiant_top10','Top 10% Radiants'],['median_rr','Median RR']];
 if(view==='world'&&!data.status.archiveOnly)countryColumns.splice(6);if(!data.status.archiveOnly&&$('estimateToggle').checked)for(let i=countryColumns.length-1;i>=0;i--){const [key,label]=countryColumns[i];if(numericColumns.includes(key))countryColumns.splice(i+1,0,['estimated_'+key,key==='players'?'Estimated players':'≈ '+label]);}
 $('countryHead').innerHTML=headings(countryColumns,countrySort,true,data.status.archiveOnly);
 const known=sorted(countries.filter(c=>c.country!=='unknown'&&matchesSubregion(c.country)).map(c=>({...c,...estimatedCounts.get(c.country)})),countrySort,'country');countryPages=Math.max(1,Math.ceil(known.length/10));countryPage=Math.min(countryPage,countryPages);
 $('countries').innerHTML=known.slice((countryPage-1)*10,countryPage*10).map(c=>countryRow(c)).join('')||'<tr><td colspan="11">No named countries in this region.</td></tr>';
 const unknown=countries.find(c=>c.country==='unknown');$('unknownCountry').innerHTML=unknown&&matchesSubregion('unknown')?countryRow(unknown,true):'';
 $('countryPageLabel').textContent=known.length?`${(countryPage-1)*10+1}–${Math.min(countryPage*10,known.length)} of ${known.length} countries`:'0 countries';
 $('countryJump').value=countryPage;$('countryJump').max=countryPages;for(const id of ['countryFirst','countryPrev'])$(id).disabled=countryPage<=1;for(const id of ['countryNext','countryLast'])$(id).disabled=countryPage>=countryPages;
 $('players').innerHTML=result.items.map(p=>`<tr><td><button data-player="${esc(p.player_key)}" data-region="${esc(p.region)}">${playerIdentity(p.display_name)}</button></td><td>${countryLabel(p.country)}</td><td>${rank(p.global_rank,p)}</td><td title="${esc(time(p.regional_rank_updated_at))}">${rank(p.regional_rank,p)}</td><td>${tierBadge(p.tier)}${p.stats_ambiguous?'*':''}</td><td>${fmt(p.rr)}</td><td>${delta(p.rr_change)}</td><td title="${p.carried_forward?'Carried observation · not refreshed':'Source observation time'}">${esc(time(p.source_updated_at))}${p.carried_forward?' · older':''}</td></tr>`).join('')||'<tr><td colspan="8">No players found.</td></tr>';
 $('globalPlayerCards').innerHTML=view==='global'?result.items.map(globalPlayerCard).join('')||'<p class="subtle">No players found for this search.</p>':'';
 renderCurrent();renderCountryView();if(view==='country'&&!data.status.archiveOnly)renderCountryMovement();if(view==='world'&&!data.status.archiveOnly)loadOverviewActivity();
 playerPages=Math.max(1,Math.ceil(result.total/playerPageSize));$('pageLabel').textContent=`${result.total?(page-1)*playerPageSize+1:0}–${Math.min(page*playerPageSize,result.total)} of ${fmt(result.total)} players`;$('playerJump').value=page;$('playerJump').max=playerPages;for(const id of ['playerFirst','prev'])$(id).disabled=page<=1;for(const id of ['next','playerLast'])$(id).disabled=page>=playerPages;
}
async function loadOverviewActivity(scope='both'){
 if(view!=='world')return;
 if(scope==='both'){await Promise.all([loadOverviewActivity('region'),loadOverviewActivity('country')]);return;}
 const minutes=scope==='region'?overviewRegionMinutes:overviewCountryMinutes,key=minutes+'|'+data.status.latestSnapshot[0]?.id,noteId=scope==='region'?'overviewRegionNote':'overviewCountryNote';
 const token=++overviewActivityVersions[scope];$(noteId).textContent='Loading activity...';
 try{let request=overviewActivityCache.get(key);if(!request){request=api('activity-overview?'+new URLSearchParams({minutes}));overviewActivityCache.set(key,request);if(overviewActivityCache.size>12)overviewActivityCache.delete(overviewActivityCache.keys().next().value);}
  const result=await request;if(token!==overviewActivityVersions[scope])return;
  if(new Date(result.window_end).getTime()!==new Date(data.status.latestSnapshot[0]?.completed_at).getTime())throw Error('A newer snapshot is available; activity will update automatically.');
  renderOverviewActivity(result,scope);
 }catch(e){overviewActivityCache.delete(key);if(token===overviewActivityVersions[scope])$(noteId).textContent=e.message;}
}
function renderOverviewActivity(activity,scope='both'){
 const cells=(r,max)=>`<td title="Distinct players with an observed RR, wins or tier change; a lower bound on activity">${fmt(r.active)}</td><td>${fmt(r.matches)}<span class="activity-bar" style="width:${max?Math.round(100*r.matches/max):0}%"></span></td><td>${fmt(r.wins)}</td><td>${fmt(r.losses)}</td>`;
 const regionMax=Math.max(1,...activity.regions.map(r=>r.matches)),countryMax=Math.max(1,...activity.countries.map(r=>r.matches));
 if(scope!=='country')$('overviewRegionActivity').innerHTML=activity.regions.map(r=>`<tr><td><a href="${esc(pageUrl('region',r.region))}">${regionSymbol(r.region)}${esc(regionName(r.region))}</a></td>${cells(r,regionMax)}</tr>`).join('');
 if(scope!=='region')$('overviewCountryActivity').innerHTML=activity.countries.map((r,i)=>`<tr><td><span class="country-position">${i+1}</span>${r.region?`<a href="${esc(pageUrl('country',r.region,r.country))}" title="Open ${esc(countryName(r.country))} in ${esc(regionName(r.region))}; most active region in this window">${countryLabel(r.country)}</a>`:countryLabel(r.country)}</td>${cells(r,countryMax)}</tr>`).join('')||'<tr><td colspan="5">No comparable country activity in this window.</td></tr>';
 const note=`${activity.minutes||60} min ending ${time(activity.window_end)} · ${activity.snapshot_count} complete snapshots`;
 if(scope!=='country')$('overviewRegionNote').textContent=note;
 if(scope!=='region')$('overviewCountryNote').textContent=note;
 if($('overviewRegionNote').textContent.includes('complete snapshots')&&$('overviewCountryNote').textContent.includes('complete snapshots')&&!document.documentElement.dataset.activityReadyMs)document.documentElement.dataset.activityReadyMs=String(Math.round(performance.now()));
 $('overviewActivityNote').textContent='Estimated player-matches = wins + assumed losses. Active Players = unique accounts with an observed RR, win-count or tier change (a lower bound). Tracked player results, not unique match lobbies; Unknown country is excluded from the top ten.';
}
async function searchOverviewPlayers(){
 const query=$('overviewSearch').value.trim();
 if(query.length<2){$('overviewSearchResults').innerHTML='';$('overviewSearchNote').textContent='Enter at least two characters to search all regions.';$('overviewSearchResults').setAttribute('aria-busy','false');return;}
 const request=overviewSearchRequest.begin();$('overviewSearchNote').textContent='Searching players…';$('overviewSearchResults').setAttribute('aria-busy','true');
 try{const result=await api('players?'+new URLSearchParams({region:'all',country:'all',search:query,page:1,size:8}),{signal:request.signal});if(!request.isCurrent())return;
  const items=Array.isArray(result.items)?result.items:[];
  $('overviewSearchResults').innerHTML=items.map(p=>`<button class="search-result" type="button" data-player="${esc(p.player_key)}" data-region="${esc(p.region)}"><strong>${playerIdentity(p.display_name)}</strong><span>${esc(regionName(p.region))} · ${countryLabel(p.country)} · ${tierBadge(p.tier)} ${fmt(p.rr)} RR</span></button>`).join('');
  $('overviewSearchNote').textContent=result.total?`${fmt(result.total)} ${result.total===1?'match':'matches'} · showing up to 8 · select a player for RR history`:'No players found. Try another name or tag.';
 }catch(e){if(request.isCurrent()){$('overviewSearchResults').innerHTML='';$('overviewSearchNote').textContent=e.name==='AbortError'?'Search cancelled.':e.message;}}
 finally{if(request.isCurrent())$('overviewSearchResults').setAttribute('aria-busy','false');}
}
const globalSearchDialog=$('globalSearchDialog'),globalSearchTrigger=$('globalSearchTrigger');let suppressSearchFocusRestore=false,playerDialogReturnFocus=null;
function openGlobalSearch(){if(!globalSearchDialog.open)globalSearchDialog.showModal();requestAnimationFrame(()=>$('overviewSearch').focus());}
function cancelGlobalSearch(){clearTimeout(overviewSearchTimer);overviewSearchRequest.cancel();$('overviewSearchResults').setAttribute('aria-busy','false');}
globalSearchTrigger.onclick=openGlobalSearch;$('globalSearchClose').onclick=()=>globalSearchDialog.close();
globalSearchDialog.addEventListener('cancel',cancelGlobalSearch);globalSearchDialog.addEventListener('close',()=>{cancelGlobalSearch();if(!suppressSearchFocusRestore)globalSearchTrigger.focus();else suppressSearchFocusRestore=false;});globalSearchDialog.addEventListener('keydown',e=>{if(e.key==='Escape'&&globalSearchDialog.open){e.preventDefault();globalSearchDialog.close();}});
window.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&!e.altKey&&e.key.toLowerCase()==='k'){const target=e.target;if(target!==$('overviewSearch')&&target instanceof HTMLElement&&target.matches('input,textarea,select,[contenteditable="true"]'))return;e.preventDefault();openGlobalSearch();}});
$('overviewSearch').oninput=()=>{cancelGlobalSearch();$('overviewSearchResults').innerHTML='';const query=$('overviewSearch').value.trim();$('overviewSearchNote').textContent=query.length<2?'Enter at least two characters to search all regions.':'Waiting to search…';if(query.length>=2)overviewSearchTimer=setTimeout(searchOverviewPlayers,250);};
$('overviewSearch').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();clearTimeout(overviewSearchTimer);searchOverviewPlayers();}else if(e.key==='ArrowDown'){const first=$('overviewSearchResults').querySelector('button[data-player]');if(first){e.preventDefault();first.focus();}}};
$('overviewSearchResults').onclick=e=>{const b=e.target.closest('[data-player]');if(b){globalSearchDialog.close();location.href=playerProfileUrl(b.dataset.player,b.dataset.region);}};
const playerDialog=document.createElement('dialog');playerDialog.id='playerDialog';playerDialog.setAttribute('aria-labelledby','overviewPlayerTitle');document.body.append(playerDialog);playerDialog.append($('overviewPlayerSection'));
const closePlayerDialog=()=>{overviewSelection=null;overviewHistoryVersion++;$('overviewPlayerSection').classList.add('hidden');if(playerDialog.open)playerDialog.close();document.body.classList.remove('player-dialog-open');const target=playerDialogReturnFocus?.isConnected?playerDialogReturnFocus:globalSearchTrigger;playerDialogReturnFocus=null;target?.focus();};
$('overviewPlayerClose').onclick=closePlayerDialog;playerDialog.addEventListener('cancel',e=>{e.preventDefault();closePlayerDialog();});
$('overviewPlayerWindow').onchange=()=>{if(overviewSelection)showSelectedPlayer(overviewSelection.player,overviewSelection.region,false,false,true);};
$('profileWindow').onchange=()=>{trendMinutes=Number($('profileWindow').value);if(view==='profile'&&mainSelection)showSelectedPlayer(mainSelection.player,mainSelection.region,false,false);};
$('overviewRegionWindow').onchange=()=>{overviewRegionMinutes=Number($('overviewRegionWindow').value);loadOverviewActivity('region');};
$('overviewCountryWindow').onchange=()=>{overviewCountryMinutes=Number($('overviewCountryWindow').value);loadOverviewActivity('country');};
async function loadCountryActivity(){
 const token=++activityVersion,key=region+'|'+activityMinutes+'|'+data.status.latestSnapshot[0]?.id;
 const noteId=view==='country'?'countryMovementNote':'countryViewNote';$(noteId).textContent='Loading observed movement…';if(view==='country'){$('countryMovementMetrics').innerHTML='';$('countryMovementTiers').innerHTML='';}else{$('countries').innerHTML='';$('unknownCountry').innerHTML='';}
 try{const [activity,boundary]=await Promise.all([api('country-activity?'+new URLSearchParams({region,minutes:activityMinutes})),api('movement-boundary-signals?'+new URLSearchParams({region,minutes:activityMinutes})).catch(()=>null)]);
  if(token!==activityVersion)return;
  if(new Date(activity.window_end).getTime()!==new Date(data.status.latestSnapshot[0]?.completed_at).getTime())throw Error('A newer snapshot is available. The page will update automatically.');
  data.countryActivity={key,...activity,boundary:boundary?.summary.snapshot_id===data.status.latestSnapshot[0]?.id?boundary:null};if(view==='country')renderCountryMovement();else if(countryMode==='movement')renderCountryView();
 }catch(e){if(token===activityVersion&&(view==='country'||countryMode==='movement'))$(noteId).textContent=e.message;}
}
function renderCountryMovement(){
 if(view!=='country')return;
 const key=region+'|'+activityMinutes+'|'+data.status.latestSnapshot[0]?.id,activity=data.countryActivity;
 if(!activity||activity.key!==key){loadCountryActivity();return;}
 const row=activity.countries.find(r=>r.country===country),values=row||{};
 renderCountryBoundaryColumns(activity.boundary);
 const metrics=[['active','Active Players'],['wins_added','Total Wins'],['losses_added','Estimated Losses'],['rr_positive','Players +RR'],['rr_negative','Players −RR'],['comparable','Compared players']];
 $('countryMovementMetrics').innerHTML=metrics.map(([key,label])=>`<div class="movement-metric" title="${esc(columnHelp[key]||'Players with at least two usable, comparable source observations in this window.')}"><span>${esc(label)}</span><strong class="${key==='rr_positive'?'positive':key==='rr_negative'?'negative':''}">${fmt(values[key]||0)}</strong></div>`).join('');
 const boundaries=[['Immortal 1','Immortal 2','up12','down21'],['Immortal 2','Immortal 3','up23','down32'],['Immortal 3','Radiant','up34','down43']];
 $('countryMovementTiers').innerHTML=boundaries.map(([from,to,up,down])=>`<tr><td>${tierBadge(from)} <span aria-hidden="true">↔</span> ${tierBadge(to)}</td><td class="positive">${fmt(values[up]||0)}</td><td class="negative">${fmt(values[down]||0)}</td></tr>`).join('');
 $('countryMovementNote').textContent=`${activityMinutes} min ending ${time(activity.window_end)} · ${activity.snapshot_count} complete snapshots · ${fmt(values.comparable||0)} compared players.${row?'':' No comparable source observations for this country in the window.'} Active Players is an observed lower bound; losses use the RR-decrease assumption.`;
 if(!document.documentElement.dataset.movementReadyMs)document.documentElement.dataset.movementReadyMs=String(Math.round(performance.now()));
}
function renderCountryView(){
 if(view!=='region')return;
 const current=countryMode==='current';$('countryMovementHelp').classList.toggle('hidden',countryMode!=='movement');$('countryEstimateControl').classList.toggle('hidden',!current||data.status.archiveOnly);$('countryActivityControl').classList.toggle('hidden',countryMode!=='movement');$('countryLegend').classList.toggle('hidden',!current||data.status.archiveOnly);
 $('estimateNote').classList.toggle('hidden',!current||!$('estimateToggle').checked);$('estimateMethod').classList.toggle('hidden',!current||!$('estimateToggle').checked);$('countryViewNote').classList.toggle('hidden',current);
 if(current)return;
 let rows,columns;
 if(countryMode==='movement'){
  const key=region+'|'+activityMinutes+'|'+data.status.latestSnapshot[0]?.id,activity=data.countryActivity;
  if(!activity||activity.key!==key){loadCountryActivity();return;}
  const observed=new Map(activity.countries.map(r=>[r.country,r]));rows=data.countries.map(c=>({country:c.country,comparable:0,active:0,wins_added:0,losses_added:0,rr_positive:0,rr_negative:0,up12:0,up23:0,up34:0,down21:0,down32:0,down43:0,...observed.get(c.country)}));
  for(const r of activity.countries)if(!rows.some(c=>c.country===r.country))rows.push(r);
  const inferred=new Map((activity.boundary?.countries||[]).map(r=>[r.country,r]));
  for(const r of activity.boundary?.countries||[])if(!rows.some(c=>c.country===r.country))rows.push({country:r.country});
  rows=rows.map(r=>({...r,possible_promotions:0,possible_demotions:0,...inferred.get(r.country)}));
  columns=[['country','Country'],['comparable','Compared players'],['active','Active Players'],['wins_added','Total Wins'],['losses_added','Estimated Losses'],['rr_positive','Players +RR'],['rr_negative','Players −RR'],['possible_promotions','Possible promotions'],['possible_demotions','Possible demotions'],['up12','Immortal 1 → Immortal 2'],['up23','Immortal 2 → Immortal 3'],['up34','Immortal 3 → Radiant'],['down43','Radiant → Immortal 3'],['down32','Immortal 3 → Immortal 2'],['down21','Immortal 2 → Immortal 1']];
  $('countryMovementDefinition').textContent=`${activityMinutes} min ending ${time(activity.window_end)} · ${activity.snapshot_count} complete snapshots (${time(activity.first_snapshot)} – ${time(activity.last_snapshot)}). Active Players = distinct compared players with a changed RR, wins or tier; this is an observed lower bound, not a match count. Total Wins counts recorded win increases in comparable observations. Estimated Losses assumes each RR-decrease interval between consecutive usable observations is one lost match, regardless of RR amount. Repeated decreases by the same player count separately. Multiple matches, penalties or offsetting results within an interval cannot be distinguished. Players +RR / −RR counts distinct players by their net observed RR change; zero-net players appear in neither column. Both source observations must be inside the window. Duplicates, carried or conflicting stats, country changes and win resets are excluded. Arrows count distinct players crossing each tier boundary; a player may appear in multiple columns or directions. Zero change does not prove inactivity. No country-allocation estimates are used; the loss column uses the stated RR-decrease assumption.`;
  $('countryViewNote').textContent=`${activityMinutes} min ending ${time(activity.window_end)} · ${activity.snapshot_count} complete snapshots. Active Players is an observed lower bound. Estimated Losses: one per observed RR-decrease interval. Possible promotions / demotions are separate inferred players in this time range; click counts for the list. Demotions cover eligible full regional scans only.`;
 }else{
  columns=[['country','Country'],['players','Players'],['radiant','Radiant %'],['immortal3','Immortal 3 %'],['immortal2','Immortal 2 %'],['immortal1','Immortal 1 %']];
  rows=data.countries.map(c=>({country:c.country,players:c.players,...Object.fromEntries(numericColumns.slice(1).map(k=>[k,c.players?100*Number(c[k]||0)/c.players:0]))}));
  $('countryViewNote').textContent='Observed rank mix: each tier as a percentage of that country’s current leaderboard players. Estimates are excluded. Unknown country stays separate; tiny samples can have extreme percentages.';
 }
 $('countryHead').innerHTML=headings(columns,countrySort,true);
 const row=c=>'<tr><td><button class="country-link" aria-expanded="'+(previewCountry===c.country)+'" data-country="'+esc(c.country)+'">'+countryLabel(c.country)+'</button></td>'+columns.slice(1).map(([k])=>`<td class="movement-cell">${k==='possible_promotions'||k==='possible_demotions'?boundaryCountCell(c,k,data.countryActivity?.boundary):countryMode==='mix'&&k!=='players'?Number(c[k]).toFixed(1)+'%':fmt(c[k])}</td>`).join('')+'</tr>';
 const known=sorted(rows.filter(c=>c.country!=='unknown'&&matchesSubregion(c.country)),countrySort,'country');countryPages=Math.max(1,Math.ceil(known.length/10));countryPage=Math.min(countryPage,countryPages);
 $('countries').innerHTML=known.slice((countryPage-1)*10,countryPage*10).map(row).join('')||'<tr><td>No country observations.</td></tr>';
 const unknown=rows.find(c=>c.country==='unknown');$('unknownCountry').innerHTML=unknown&&matchesSubregion('unknown')?row(unknown):'';
 $('countryPageLabel').textContent=known.length?`${(countryPage-1)*10+1}–${Math.min(countryPage*10,known.length)} of ${known.length} countries`:'0 countries';
 $('countryJump').value=countryPage;$('countryJump').max=countryPages;for(const id of ['countryFirst','countryPrev'])$(id).disabled=countryPage<=1;for(const id of ['countryNext','countryLast'])$(id).disabled=countryPage>=countryPages;
}
function renderCurrent(){
 const hasCountry=country!=='all',totals=hasCountry?data.countries.find(c=>c.country===country):data.regions.find(r=>r.region===region),archive=Boolean(data.status.archiveOnly),estimatedArchive=archive&&$('currentEstimateToggle').checked;
 const cols=archive?(estimatedArchive?['players','radiant','immortal_all','immortal','immortal3','immortal2','immortal1','estimated_immortal3','estimated_immortal2','estimated_immortal1']:['players','radiant','immortal_all','immortal','immortal3','immortal2','immortal1']):numericColumns,enabled=!archive&&view!=='region'&&$('estimateToggle').checked;
 $('currentEstimateToggle').checked=archive?estimatedArchive:enabled;
 $('currentEstimateControl').querySelector('input').nextSibling.textContent=archive?' Estimate split Immortal by RR thresholds':' Show estimates for players and rank tiers';
 $('currentEstimateNote').textContent=archive?`${totals?.source_basis||'Archived ladder'} · ${totals?.tier_semantics||'Immortal source labels distinguish combined and split tiers.'} Threshold profile: ${totals?.threshold_profile||'regional policy'}. ${totals?.threshold_policy||'Estimated subtiers use RR thresholds.'}`:'Estimated totals include allocated unknown countries; observed counts stay unchanged.';
 $('currentEstimateNote').classList.toggle('hidden',!archive&&!enabled);
 const labels={players:'Players',radiant:'Radiant',immortal_all:'Immortal total',immortal:'Immortal combined only',immortal3:'Reported Immortal 3',immortal2:'Reported Immortal 2',immortal1:'Reported Immortal 1'};
 $('currentHead').innerHTML='<tr><th>Scope</th>'+cols.map(k=>`<th>${archive?esc(archive&&k.startsWith('estimated_')?'Estimated '+labels[k.replace('estimated_','')]:labels[k]||k):rankLabel(labels[k]||k)}</th>${enabled?`<th>Estimated ${rankLabel(labels[k])}</th>`:''}`).join('')+'</tr>';
 const estimate=hasCountry?estimatedCounts.get(country):null;
 $('currentNumbers').innerHTML=totals?`<tr><td>${esc(hasCountry?countryName(country):regionName(region))}</td>${cols.map(k=>`<td${k.startsWith('estimated_')?' class="estimate-value"':''}>${fmt(totals[k])}</td>${enabled?`<td class="estimate-value">${hasCountry?(estimate?'≈ '+fmt(estimate['estimated_'+k]):'—'):'≈ '+fmt(totals[k])}</td>`:''}`).join('')}</tr>`:'<tr><td colspan="11">No current observations.</td></tr>';
}
function setProfileScope(){
 for(const id of ['countriesDirectory','regionExperienceTabs','regionTabHint','regionOverviewPanel','regionDistributionPanel','regionSection','regionTableSection','overviewActivitySection','currentSection','countryMovementSection','playerSection','trendLayout','countrySection','countryPreview','historySection','historicalTrendDisclosure','timelineSection','scopeSection'])$(id).classList.add('hidden');
 $('dataConfidence').classList.remove('hidden');$('dataFreshness').classList.remove('hidden');$('dataFreshness').textContent='Player metrics are tied to the latest completed snapshot. Source observation times remain visible in each panel.';
 $('playerProfileIntro').classList.remove('hidden');$('playerProfileWorkspace').classList.remove('hidden');$('playerProfileWorkspace').appendChild($('selectedPlayerSection'));$('selectedPlayerSection').classList.remove('hidden');$('selectedPlayerSection').classList.add('player-profile-section');
 $('profileBack').href=pageUrl('global');$('profileWindow').value=String(trendMinutes);$('pageTitle').textContent='Player profile';document.title='Player profile · Valorant Ranked Observatory';$('subtitle').textContent='A source-aware view of ranked position, history and competition.';
 $('breadcrumbs').innerHTML=`<a class="breadcrumb-link" href="${esc(pageUrl('world'))}">Overview</a><span class="subtle">›</span><a class="breadcrumb-link" href="${esc(pageUrl('global'))}">Global leaderboard</a><span class="subtle">›</span><span class="breadcrumb-current" aria-current="page">Player profile</span>`;
 $('playerProfileHeading').textContent=requestedProfilePlayer||'Player profile';$('playerProfileMeta').textContent=`${regionName(requestedProfileRegion)} · Loading current snapshot evidence…`;
}
let archiveTab='summary',archiveRegion='all',archiveCountry='all',archiveSearch='',archivePage=1,archivePayload=null,archiveSearchTimer;
function setArchiveScope(){
 for(const id of ['countriesDirectory','regionExperienceTabs','regionTabHint','regionOverviewPanel','regionDistributionPanel','regionSection','regionTableSection','overviewActivitySection','currentSection','countryMovementSection','playerSection','trendLayout','timelineSection','scopeSection','selectedPlayerSection','countrySection','regionPage','countryPreview','historySection','historicalTrendDisclosure','playerProfileIntro','playerProfileWorkspace','trendsPageIntro','trendsPageWorkspace','globalPlayerCards','overviewPlayerSection','previewPlayerSection','dataFreshness'])$(id).classList.add('hidden');
 $('masthead').classList.remove('region-masthead','country-masthead','overview-masthead');$('regionScenery').classList.add('hidden');$('refresh').classList.add('hidden');
 $('archivePageIntro').classList.remove('hidden');$('archivePageWorkspace').classList.remove('hidden');
 $('pageTitle').textContent='Act archive';document.title='Act archive · Valorant Ranked Observatory';$('subtitle').textContent='Published end-of-Act standings and source coverage';
 $('breadcrumbs').innerHTML='<a class="breadcrumb-link" href="/overview">Overview</a><span class="subtle">›</span><span class="breadcrumb-current" aria-current="page">Act archive</span>';
}
function archiveTabs(){return `<nav class="archive-tabs" aria-label="Archive sections" role="tablist">${[['summary','Summary'],['countries','Countries'],['players','Players'],['coverage','Source coverage']].map(([key,label])=>`<button type="button" role="tab" aria-selected="${archiveTab===key}" data-archive-tab="${key}">${label}</button>`).join('')}</nav>`;}
function renderArchiveExplorer(){
 if(!archivePayload)return;const {status,acts,countries,players}=archivePayload,progress=status.archiveProgress||{},runs=status.runs||[],label=$('actFilter').selectedOptions[0]?.textContent?.replace(/\s*·\s*Live$/,'')||'Archived Act';
 $('archivePageTitle').textContent=label+' archive';$('pageTitle').textContent=label+' archive';document.title=label+' archive · Valorant Ranked Observatory';
 const selected=acts.find(a=>a.act===selectedAct),completed=Number(progress.completedSources||selected?.completed_sources||0),sourceTotal=Number(progress.totalSources||selected?.total_sources||7);
 let body='';
 if(archiveTab==='summary'){
  body=`<div class="archive-metrics"><article><span>Global source rows</span><strong>${fmt(progress.players||0)}</strong><small>Rows saved in this Act’s global source ladder</small></article><article><span>Source ladders complete</span><strong>${fmt(completed)} / ${fmt(sourceTotal)}</strong><small>Completion reflects source status</small></article><article><span>Fetched pages</span><strong>${fmt(progress.pages||0)}</strong><small>Across recorded source ladders</small></article><article><span>Regional archived players</span><strong>${fmt(progress.sourceRows||0)}</strong><small>Sum of source-reported player rows</small></article></div><section class="archive-panel"><div class="section-heading"><div><h3>Snapshot publication</h3><p class="subtle">Saved source timestamps: ${esc(time(progress.sourceUpdatedAt||status.latestSnapshot?.[0]?.completed_at))}</p></div><span class="archive-state ${completed===sourceTotal?'is-complete':'is-partial'}">${completed===sourceTotal?'Complete':'Partial coverage'}</span></div><p class="archive-note">Country and tier summaries are calculated from completed regional source ladders. If a summary is absent, the underlying player rows and source status remain separately visible.</p><p class="archive-note">${esc(progress.sourceBasis||'Source rows are preserved per leaderboard ladder. Reported tier labels remain distinct from any threshold-based estimate.')}</p><button type="button" class="archive-primary" data-archive-tab="players">Browse archived players <span aria-hidden="true">→</span></button></section>`;
 } else if(archiveTab==='countries'){
  body=`<section class="archive-panel"><div class="archive-toolbar"><label>Regional source<select data-archive-region><option value="all">Global source</option>${Object.entries(status.regions||{}).map(([key,name])=>`<option value="${esc(key)}" ${archiveRegion===key?'selected':''}>${esc(name)}</option>`).join('')}</select></label><span class="subtle">Country counts are observed rows from the selected source.</span></div><div class="archive-table-wrap"><table><thead><tr><th>Country</th><th>Players</th><th>Radiant</th><th>Immortal total</th><th>Combined only</th><th>Reported I3</th><th>Reported I2</th><th>Reported I1</th></tr></thead><tbody>${countries.map(row=>`<tr><td>${countryLabel(row.country)}</td><td>${fmt(row.players)}</td><td>${fmt(row.radiant)}</td><td>${fmt(row.immortal_all)}</td><td>${fmt(row.combined_immortal)}</td><td>${fmt(row.immortal3)}</td><td>${fmt(row.immortal2)}</td><td>${fmt(row.immortal1)}</td></tr>`).join('')||'<tr><td colspan="8">No country summary is available for this source.</td></tr>'}</tbody></table></div><p class="archive-note">Immortal total includes combined-only and reported I1/I2/I3 rows. Values are source summaries; no missing records are filled with zero.</p></section>`;
 } else if(archiveTab==='players'){
  const total=Number(players.total||0),pages=Math.max(1,Math.ceil(total/25));
  body=`<section class="archive-panel"><div class="archive-toolbar archive-player-filters"><label>Region<select data-archive-region><option value="all">All regions · global source</option>${Object.entries(status.regions||{}).map(([key,name])=>`<option value="${esc(key)}" ${archiveRegion===key?'selected':''}>${esc(name)}</option>`).join('')}</select></label><label>Country<select data-archive-country><option value="all">All countries</option>${(status.countries?Object.entries(status.countries):[]).map(([key,name])=>`<option value="${esc(key)}" ${archiveCountry===key?'selected':''}>${esc(name)}</option>`).join('')}<option value="unknown" ${archiveCountry==='unknown'?'selected':''}>Unknown country</option></select></label><label class="archive-search">Search player<input type="search" data-archive-search maxlength="100" value="${esc(archiveSearch)}" placeholder="Riot name or tag"></label></div><p class="archive-note">Showing ${total?fmt((archivePage-1)*25+1):'0'}–${fmt(Math.min(archivePage*25,total))} of ${fmt(total)} archived player rows · source rank, not a current rank.</p><div class="archive-table-wrap"><table><thead><tr><th>Player</th><th>Region</th><th>Country</th><th>Global rank</th><th>Regional rank</th><th>RR</th><th>Reported tier</th><th>Source updated</th></tr></thead><tbody>${(players.items||[]).map(row=>`<tr><td><a class="archive-player-link" href="${esc(playerProfileUrl(row.display_name,row.region))}">${playerIdentity(row.display_name)}</a></td><td>${esc(regionName(row.region))}</td><td>${countryLabel(row.country)}</td><td>${fmt(row.global_rank)}</td><td>${fmt(row.regional_rank)}</td><td>${fmt(row.rr)}</td><td>${esc(row.tier)}</td><td>${esc(time(row.source_updated_at))}</td></tr>`).join('')||'<tr><td colspan="8">No archived players match these filters.</td></tr>'}</tbody></table></div><div class="archive-pagination"><button type="button" data-archive-page="prev" ${archivePage<=1?'disabled':''}>Previous</button><span>Page ${fmt(archivePage)} of ${fmt(pages)}</span><button type="button" data-archive-page="next" ${archivePage>=pages?'disabled':''}>Next</button></div></section>`;
 } else {
  body=`<section class="archive-panel"><div class="section-heading"><div><h3>Source publication matrix</h3><p class="subtle">Each row represents a source ladder recorded for this Act.</p></div><span class="archive-state ${completed===sourceTotal?'is-complete':'is-partial'}">${fmt(completed)} / ${fmt(sourceTotal)} complete</span></div><div class="archive-table-wrap"><table><thead><tr><th>Source</th><th>Status</th><th>Pages</th><th>Reported player rows</th><th>Source updated</th><th>Last completed page</th></tr></thead><tbody>${runs.map(run=>`<tr><td>${esc(run.source_region==='global'?'Global':regionName(run.source_region))}</td><td><span class="archive-state ${run.status==='COMPLETE'?'is-complete':'is-partial'}">${esc(run.status||'Unknown')}</span></td><td>${fmt(run.pages)}</td><td>${fmt(run.players)}</td><td>${esc(time(run.source_updated_at))}</td><td>${esc(time(run.completed_at))}</td></tr>`).join('')||'<tr><td colspan="6">No source publication details are available.</td></tr>'}</tbody></table></div><p class="archive-note">Incomplete sources are labeled by their recorded state. Source errors and retry details are not exposed in this public view.</p></section>`;
 }
 $('archivePageWorkspace').innerHTML=`${archiveTabs()}${body}`;
}
async function refreshArchiveExplorer(version){
 setArchiveScope();$('message').textContent='Loading archived snapshot…';
 try{
  const q=new URLSearchParams({act:selectedAct});const p=new URLSearchParams({region:archiveRegion,country:archiveCountry,page:String(archivePage),size:'25',search:archiveSearch,sort:'globalRank',direction:'asc',act:selectedAct});
  const [status,acts,countries,players]=await Promise.all([api('status?'+q),api('acts'),api('countries?'+new URLSearchParams({region:archiveRegion,country:'all',act:selectedAct})),api('players?'+p)]);if(version!==generation)return;
  archivePayload={status,acts,countries,players};countryNames=status.countries||{};regionNames=status.regions||{};$('lastUpdated').textContent=time(status.latestSnapshot?.[0]?.completed_at);$('message').textContent='';
  const progress=status.archiveProgress||{};$('archivePageTitle').textContent=$('actFilter').selectedOptions[0]?.textContent?.replace(/\s*·\s*Live$/,'')+' archive';$('subtitle').textContent=`${fmt(progress.players||0)} global source rows · ${fmt(progress.completedSources||0)} of ${fmt(progress.totalSources||7)} source ladders complete`;
  renderArchiveExplorer();if(!document.documentElement.dataset.coreReadyMs)document.documentElement.dataset.coreReadyMs=String(Math.round(performance.now()));
 }catch(error){if(version===generation){$('message').textContent=error.message;$('archivePageWorkspace').innerHTML=`${archiveTabs()}<section class="archive-panel archive-error" role="alert"><h3>Archive data unavailable</h3><p>${esc(error.message)}</p><button type="button" id="archiveRetry">Retry archive load</button></section>`;}}
}
function setCompareScope(){
 for(const id of ['countriesDirectory','regionExperienceTabs','regionTabHint','regionOverviewPanel','regionDistributionPanel','regionSection','regionTableSection','overviewActivitySection','currentSection','countryMovementSection','playerSection','trendLayout','timelineSection','scopeSection','selectedPlayerSection','countrySection','regionPage','countryPreview','historySection','historicalTrendDisclosure','playerProfileIntro','playerProfileWorkspace','trendsPageIntro','trendsPageWorkspace','archivePageIntro','archivePageWorkspace','globalPlayerCards','overviewPlayerSection','previewPlayerSection','dataFreshness'])$(id).classList.add('hidden');
 $('comparePageIntro').classList.remove('hidden');$('comparePage').classList.remove('hidden');
 $('pageTitle').textContent='Compare fairly';document.title='Compare fairly · Valorant Ranked Observatory';$('subtitle').textContent='Same Act, labeled source scope, evidence-backed differences.';
 $('masthead').classList.remove('region-masthead','country-masthead','overview-masthead');$('regionScenery').classList.add('hidden');
}function setWatchlistScope(){
 for(const id of ['countriesDirectory','regionExperienceTabs','regionTabHint','regionOverviewPanel','regionDistributionPanel','regionSection','regionTableSection','overviewActivitySection','currentSection','countryMovementSection','playerSection','trendLayout','timelineSection','scopeSection','selectedPlayerSection','countrySection','regionPage','countryPreview','historySection','historicalTrendDisclosure','playerProfileIntro','playerProfileWorkspace','trendsPageIntro','trendsPageWorkspace','comparePageIntro','comparePage','archivePageIntro','archivePageWorkspace','globalPlayerCards','overviewPlayerSection','previewPlayerSection','dataFreshness'])$(id).classList.add('hidden');
 $('watchlistPageIntro').classList.remove('hidden');$('watchlistPageWorkspace').classList.remove('hidden');$('dataFreshness').classList.add('hidden');
 $('pageTitle').textContent='Your watchlist';document.title='Your watchlist · Valorant Ranked Observatory';$('subtitle').textContent='Save players, countries, regions and Acts to find them again.';
 $('masthead').classList.remove('region-masthead','country-masthead','overview-masthead');$('regionScenery').classList.add('hidden');
}function setSettingsScope(){
 for(const id of ['countriesDirectory','regionExperienceTabs','regionTabHint','regionOverviewPanel','regionDistributionPanel','regionSection','regionTableSection','overviewActivitySection','currentSection','countryMovementSection','playerSection','trendLayout','timelineSection','scopeSection','selectedPlayerSection','countrySection','regionPage','countryPreview','historySection','historicalTrendDisclosure','playerProfileIntro','playerProfileWorkspace','trendsPageIntro','trendsPageWorkspace','comparePageIntro','comparePage','archivePageIntro','archivePageWorkspace','watchlistPageIntro','watchlistPageWorkspace','globalPlayerCards','overviewPlayerSection','previewPlayerSection','dataFreshness','dataConfidence'])$(id)?.classList.add('hidden');
 $('settingsPageIntro').classList.remove('hidden');$('settingsPageWorkspace').classList.remove('hidden');
 $('pageTitle').textContent='Display settings';document.title='Display settings · Valorant Ranked Observatory';$('subtitle').textContent='Choose saved display preferences for your account.';
 $('masthead').classList.remove('region-masthead','country-masthead','overview-masthead');$('regionScenery').classList.add('hidden');
}function setTrendsScope(){
 for(const id of ['countriesDirectory','regionExperienceTabs','regionTabHint','regionOverviewPanel','regionDistributionPanel','regionSection','regionTableSection','overviewActivitySection','currentSection','countryMovementSection','playerSection','trendLayout','countrySection','countryPreview','historySection','timelineSection','scopeSection','playerProfileIntro','playerProfileWorkspace','selectedPlayerSection'])$(id).classList.add('hidden');
 $('dataConfidence').classList.remove('hidden');$('dataFreshness').classList.add('hidden');$('masthead').classList.remove('region-masthead','country-masthead','overview-masthead');$('regionScenery').classList.add('hidden');
 $('trendsPageIntro').classList.remove('hidden');$('trendsPageWorkspace').classList.remove('hidden');$('trendsPageWorkspace').appendChild($('historicalTrendDisclosure'));$('historicalTrendDisclosure').classList.remove('hidden');$('historicalTrendDisclosure').open=true;$('historicalTrendDisclosure').querySelector('summary').hidden=true;
 $('pageTitle').textContent='Historical trends';document.title='Historical trends · Valorant Ranked Observatory';$('subtitle').textContent='Compare observed ranked populations across completed Acts and the latest complete snapshot.';
 $('breadcrumbs').innerHTML='<a class="breadcrumb-link" href="/overview">Overview</a><span class="subtle">›</span><span class="breadcrumb-current" aria-current="page">Historical trends</span>';$('actTrendExport').disabled=true;
}
function setScope(){
 if(view==='profile'){setProfileScope();return;}
 if(view==='trends'){setTrendsScope();return;}
 if(view==='compare'){setCompareScope();return;}
 if(view==='watchlist'){setWatchlistScope();return;}
 if(view==='settings'){setSettingsScope();return;}
 const historical=Boolean(data?.status?.archiveOnly),hasRegion=region!=='all',hasCountry=hasRegion&&country!=='all',detail=view==='region'||view==='country';
 $('overviewActivitySection').classList.toggle('hidden',view!=='world'||historical);$('masthead').classList.toggle('overview-masthead',view==='world');
 $('regionSection').classList.toggle('hidden',detail);$('regionTableSection').classList.toggle('hidden',detail);
 $('trendLayout').classList.toggle('hidden',!detail&&!hasRegion);$('trendLayout').classList.toggle('world-preview',false);$('trendLayout').classList.toggle('world-country-pair',view==='world');
 $('currentSection').classList.toggle('hidden',!detail);$('countryMovementSection').classList.toggle('hidden',historical||view!=='country'||!hasCountry);
 $('masthead').classList.toggle('region-masthead',true);$('regionScenery').classList.remove('hidden');$('masthead').classList.toggle('country-masthead',view==='country');$('regionScenery').src=view==='country'?(CountryHeaderScenery[country]||'/scenery/valorant-header.jpg'):view==='world'?'/scenery/valorant-header.jpg':'/scenery/'+region+'.jpg';$('regionScenery').onerror=function(){this.onerror=null;this.src='/scenery/valorant-header.jpg';};
 $('refresh').classList.add('hidden');$('currentTitle').textContent=historical?'Archived act snapshot':detail?'Current Player Data':'Current snapshot';$('currentEstimateControl').classList.toggle('hidden',!detail||(!historical&&view==='region'));
 $('countryViewControls').classList.toggle('hidden',view!=='region'||historical);$('moverCountryControl').classList.toggle('hidden',view!=='region'||historical);
 const supportsSubregions=Object.hasOwn(codes,region);$('subregionControl').classList.toggle('hidden',view!=='region'||!supportsSubregions);
 if(!supportsSubregions||subregion!=='all'&&subregion!=='other'&&!subregionGroups()[subregion])subregion='all';
 $('subregionFilter').innerHTML='<option value="all">All subregions</option>'+Object.entries(subregionGroups()).map(([key,g])=>`<option value="${key}">${esc(g.name)}</option>`).join('')+'<option value="other">Other / Unknown</option>';$('subregionFilter').value=subregion;
 $('dataFreshness').classList.add('hidden');
 $('moverCountry').innerHTML='<option value="all">All countries</option>'+data.countries.slice().sort((a,b)=>countryName(a.country).localeCompare(countryName(b.country))).map(c=>`<option value="${esc(c.country)}">${esc(countryName(c.country))}</option>`).join('');$('moverCountry').value=moverCountry;
 $('timelineSection').classList.toggle('hidden',detail||historical);$('selectedPlayerSection').classList.toggle('hidden',!detail);
 $('moverControls').classList.toggle('hidden',historical);$('changeWindowLabel').classList.toggle('hidden',historical);$('movers').classList.toggle('hidden',historical);
 if(detail)$('moverControls').appendChild($('timeRangeControl'));
 else $('timelineSection').appendChild($('timeRangeControl'));

 if(view==='world')$('trendLayout').appendChild($('countrySection'));
 else if(view==='region'){$('trendLayout').parentNode.insertBefore($('countrySection'),$('trendLayout'));$('trendLayout').parentNode.insertBefore($('countryPreview'),$('trendLayout'));}
 else {$('countryPreview').parentNode.insertBefore($('countrySection'),$('countryPreview'));$('trendLayout').parentNode.insertBefore($('countryMovementSection'),$('trendLayout'));$('trendLayout').parentNode.insertBefore($('playerSection'),$('trendLayout'));}
 const totals=hasCountry?data.countries.find(c=>c.country===country):data.regions.find(r=>r.region===region);
 const displayColumns=historical?['players','radiant','immortal_all','immortal','immortal3','immortal2','immortal1']:numericColumns;
 $('currentHead').innerHTML='<tr><th>Scope</th>'+displayColumns.map(k=>`<th>${historical?esc(({players:'Players',radiant:'Radiant',immortal_all:'Immortal total',immortal:'Immortal combined only',immortal3:'Reported Immortal 3',immortal2:'Reported Immortal 2',immortal1:'Reported Immortal 1'}[k])):rankLabel(({players:'Players',radiant:'Radiant',immortal3:'Immortal 3',immortal2:'Immortal 2',immortal1:'Immortal 1'}[k]))}</th>`).join('')+'</tr>';
 $('currentNumbers').innerHTML=totals?`<tr><td>${esc(hasCountry?countryName(country):regionName(region))}</td>${displayColumns.map(k=>`<td>${fmt(totals[k])}</td>`).join('')}</tr>`:'<tr><td colspan="6">No current observations.</td></tr>';

 $('countrySection').classList.toggle('hidden',!hasRegion||view==='country');$('playerSection').classList.toggle('hidden',view!=='country'||!hasCountry);$('scopeSection').classList.toggle('hidden',!detail);
 const key=region+'|'+country;if(expandedScope!==key){$('countrySection').open=hasRegion;$('playerSection').open=hasCountry;expandedScope=key;}
 const title=view==='country'&&hasCountry?countryName(country)+' · '+regionName(region):view==='region'&&hasRegion?regionName(region):'Valorant Ranked Observatory';
 $('pageTitle').innerHTML=(view==='country'&&hasCountry?flag(country):view==='region'&&hasRegion?regionSymbol(region)+' ':'')+esc(title);document.title=title+(view!=='world'?' · Valorant Ranked Observatory':'');
 $('subtitle').textContent=historical?'Archived PC ranked leaderboard snapshot':detail?'PC ranked leaderboard':'Ranked players, regional standings and competitive activity';
 $('breadcrumbs').innerHTML=hasRegion?`<a class="breadcrumb-link" href="${esc(pageUrl('world'))}">Overview</a><span class="subtle">›</span>${hasCountry?`<a class="breadcrumb-link" href="${esc(pageUrl('region',region))}">${esc(regionName(region))}</a><span class="subtle">›</span><span class="breadcrumb-current" aria-current="page">${esc(countryName(country))}</span>`:`<span class="breadcrumb-current" aria-current="page">${esc(regionName(region))}</span>`}`:'';
 $('regionPage').href=pageUrl('region',region);$('regionPage').hidden=view==='region';$('countryPage').href=pageUrl('country',region,country);$('countryPage').hidden=view==='country';
 $('countrySummary').textContent=regionName(region)+' · Countries';$('playerSummary').textContent=countryName(country)+' · Players';$('countryMovementTitle').textContent=countryName(country)+' · Movement';$('playerTableNote').textContent='Latest complete leaderboard · '+playerPageSize+' players per page. Select a player to inspect their RR history below. Δ RR compares the two latest snapshots.';
 const scope=hasCountry?countryName(country):hasRegion?regionName(region):'All regions';$('timelineTitle').textContent=scope+' · population over time';$('chartModeLabel').classList.toggle('hidden',hasRegion);
 if(view==='global'){
   for(const id of ['overviewActivitySection','regionSection','regionTableSection','regionExperienceTabs','regionTabHint','regionOverviewPanel','regionDistributionPanel','currentSection','countryMovementSection','countrySection','trendLayout','countryPreview'])$(id).classList.add('hidden');
   $('playerSection').classList.remove('hidden');$('playerSection').open=true;$('playerSummary').textContent='Global leaderboard';$('playerSearch').value=search;$('globalSortWrap').classList.remove('hidden');$('globalSort').value=sortKey+':'+sortDirection;$('playerTableNote').textContent='All tracked players · sorted across the full result set · 10 per page.';
   $('pageTitle').textContent='Global leaderboard';document.title='Global leaderboard · Valorant Ranked Observatory';$('regionScenery').src='/scenery/valorant-header.jpg';$('countryPage').hidden=true;
   syncGlobalUrl(false);
 }
 updateShellBreadcrumbs();
}
function drawLines(rows,id,series,dateKey='completed_at',fit=false){
 const canvas=$(id),w=Math.max(280,canvas.clientWidth||900),h=230,ratio=globalThis.devicePixelRatio||1,c=canvas.getContext('2d');canvas.width=w*ratio;canvas.height=h*ratio;c.scale(ratio,ratio);c.clearRect(0,0,w,h);
 if(!rows.length){c.fillStyle='#91a4bc';c.font='14px system-ui';c.fillText('No observations in this window.',30,110);return;}
 const points=[...rows].sort((a,b)=>new Date(a[dateKey])-new Date(b[dateKey])),xs=points.map(r=>new Date(r[dateKey]).getTime());let minX=Math.min(...xs),maxX=Math.max(...xs);if(minX===maxX){minX-=60000;maxX+=60000;}
 const values=series.flatMap(s=>points.filter(p=>p[s.key]!=null).map(p=>Number(p[s.key]))),peak=Math.max(1,...values),floor=values.length?Math.min(...values):0,spread=Math.max(10,peak-floor);
 const minY=fit?Math.max(0,Math.floor(floor-Math.max(2,spread*.15))):0,maxY=fit?Math.max(minY+4,Math.ceil(peak+Math.max(2,spread*.15))):peak*1.1;
 const x=t=>58+(w-82)*(t-minX)/(maxX-minX),y=v=>184-160*(v-minY)/(maxY-minY);
 c.font='11px system-ui';for(let i=0;i<=4;i++){const v=minY+(maxY-minY)*i/4;c.strokeStyle='#253144';c.beginPath();c.moveTo(58,y(v));c.lineTo(w-24,y(v));c.stroke();c.fillStyle='#91a4bc';c.fillText(Math.round(v).toLocaleString(),5,y(v)+4);}
 c.fillStyle='#91a4bc';c.fillText(time(xs[0]),58,212);c.textAlign='right';c.fillText(time(xs[xs.length-1]),w-24,226);c.textAlign='left';
 for(const s of series){c.strokeStyle=s.color;c.fillStyle=s.color;c.setLineDash(s.dashed?[6,4]:[]);c.beginPath();let previous=null;points.forEach((p,i)=>{if(p[s.key]==null){previous=null;return;}if(previous==null||(fit&&xs[i]-xs[previous]>45*60000))c.moveTo(x(xs[i]),y(Number(p[s.key])));else c.lineTo(x(xs[i]),y(Number(p[s.key])));previous=i;});c.stroke();c.setLineDash([]);points.forEach((p,i)=>{if(p[s.key]==null)return;c.beginPath();c.arc(x(xs[i]),y(Number(p[s.key])),3,0,Math.PI*2);c.fill();});}
}
function renderTrends(){
 const t=data.trends;let points=t.points,series=[{key:'radiant',label:'Radiant',color:'#f2d589'},{key:'immortal3',label:'Immortal 3',color:'#b3a4ed'},{key:'immortal2',label:'Immortal 2',color:'#73c9c4'},{key:'immortal1',label:'Immortal 1',color:'#7da5dc'}];
 if(region==='all'&&$('chartMode').value==='regions'){
  const byTime=new Map();for(const p of t.regionPoints){if(!byTime.has(p.completed_at))byTime.set(p.completed_at,{completed_at:p.completed_at});byTime.get(p.completed_at)[p.region]=p.players;}points=[...byTime.values()];const colors=['#73c9c4','#f2d589','#b3a4ed','#7da5dc','#ef9baf','#accd81'];series=Object.keys(regionNames).sort().map((r,i)=>({key:r,label:regionName(r),color:colors[i]}));
 }
 if(view==='world'&&!worldTrendCountry){$('timelineTitle').textContent='Country population over time';$('tierLegend').innerHTML='';$('tierChart').classList.add('hidden');$('timelineNote').textContent='';}else{$('tierChart').classList.remove('hidden');if(view==='world')$('timelineTitle').textContent=countryName(worldTrendCountry)+' · population over time';
 $('tierLegend').innerHTML=series.map(s=>`<span><i style="background:${s.color}"></i>${rankLabel(s.label)}</span>`).join('');drawLines(points,'tierChart',series);
 $('timelineNote').textContent=`${t.snapshotCount} snapshot${t.snapshotCount===1?'':'s'} · window ends at last update · includes carried players`;
 }
 const label=$('trendWindow').options?$('trendWindow').options[$('trendWindow').selectedIndex].text:trendMinutes+' min';
 $('changeWindowLabel').textContent=`${label} · ${t.snapshotCount} snapshots available`;
 $('movers').innerHTML=moverTable(t.movers,t.snapshotCount,direction);
 if(mainSelection)showSelectedPlayer(mainSelection.player,mainSelection.region,false,false);if(view==='world'&&overviewSelection)showSelectedPlayer(overviewSelection.player,overviewSelection.region,false,false,true);

}
async function refresh(){const version=++generation;try{
 if(view==='countries'){await globalThis.loadCountriesDirectory?.();return;}
 if(view==='profile'){await refreshPlayerProfilePage(version);return;}
 if(view==='trends'){await refreshTrendsPage(version);return;}
 if(view==='compare'){setCompareScope();await globalThis.refreshComparePage?.();return;}
 if(view==='watchlist'){setWatchlistScope();await globalThis.refreshWatchlistPage?.();return;}
 if(view==='settings'){setSettingsScope();await globalThis.refreshSettingsPage?.();return;}
 if(view==='archive'){await refreshArchiveExplorer(version);return;}
 if(selectedAct&&selectedAct!==currentAct){await refreshArchived(version);return;}
 if(view==='global'){await refreshGlobalPage(version);return;}
 if(view==='country'){await refreshCountryPage(version);return;}
 const detailed=view!=='world'||region!=='all',filter=new URLSearchParams({region,country}),statusRequest=api('status');
 const [status,countries,regions,result,trends,modelEvidence,confidence,boundary]=await Promise.all([statusRequest,detailed?api('countries?'+new URLSearchParams({region,country:'all'})):[],api('regions?country=all'),detailed?api('players?'+filter+'&'+new URLSearchParams({page,size:playerPageSize,search})):{items:[],total:0},detailed?api('trends?'+new URLSearchParams({region,country:view==='world'?(worldTrendCountry||'all'):view==='region'?moverCountry:country,minutes:trendMinutes,direction})):{points:[],regionPoints:[],movers:[],snapshotCount:0},detailed&&($('estimateToggle').checked||$('currentEstimateToggle').checked)?api('country-model'):statusRequest.then(s=>data?.modelEvidence?.policyVersion===3&&data.modelEvidence.snapshotId===s.latestSnapshot[0]?.id?data.modelEvidence:{snapshotId:s.latestSnapshot[0]?.id}),api('confidence?'+filter),view==='world'?null:api('boundary-signals?'+filter).catch(()=>null)]);if(version!==generation)return;
 if(modelEvidence.snapshotId!==status.latestSnapshot[0]?.id)throw new Error('Snapshot changed while loading country estimates. Refresh to load a consistent comparison.');
 const evidenceRows=CountryModel.inputRows(modelEvidence,region);
 if(region!=='all')for(const c of evidenceRows){const raw=countries.find(r=>r.country===c.country);if(!raw||numericColumns.some(k=>Number(raw[k]||0)!==Number(c[k]||0)))throw new Error('Snapshot changed while loading country estimates. Refresh to retry.');}
 for(const r of modelEvidence.recoveries||[])if(r.region===region&&!countries.some(c=>c.country===r.country))countries.push({country:r.country,players:0,radiant:0,immortal3:0,immortal2:0,immortal1:0});
 const countryActivity=data?.countryActivity;data={status,countries,regions,result,trends,modelEvidence,countryActivity,confidence};countryNames=status.countries;regionNames=status.regions;$('message').textContent='';$('lastUpdated').textContent=time(status.latestSnapshot[0]?.completed_at);
 const completed=status.latestSnapshot[0]?.completed_at,age=completed?(Date.now()-new Date(completed).getTime())/60000:Infinity;
 $('dataFreshness').textContent=completed?`${age>40?'Data is delayed. ':''}Time filters end at the latest complete snapshot: ${time(completed)}, not the current time. ${status.running?'Collection is in progress; results appear after completion.':status.enabled?'Collection is scheduled every 20 minutes.':'Collection is disabled.'}`:'Waiting for the first complete snapshot.';
 if(confidence.snapshot_id!==status.latestSnapshot[0]?.id)throw new Error('Snapshot changed; confidence will refresh automatically.');
 renderConfidence(status,confidence);
 setScope();renderTables();renderTrends();renderBoundaryWatch(boundary);if(previewCountry)showCountryPreview(previewCountry,false);
 globalThis.observatoryRegionData={view,region,summary:regions.find(r=>r.region===region)||null,boundary:countries.find(c=>c.radiant_threshold!=null)?.radiant_threshold??null,archive:Boolean(status.archiveOnly)};globalThis.dispatchEvent(new CustomEvent('observatory:region-data',{detail:globalThis.observatoryRegionData}));
 if(!document.documentElement.dataset.coreReadyMs)document.documentElement.dataset.coreReadyMs=String(Math.round(performance.now()));
 }catch(e){if(version===generation)$('message').textContent=e.message;}}
async function refreshArchived(version){
 const status=await api('status');if(version!==generation)return;
 const detailed=view==='region'||view==='country',isGlobal=view==='global';let countries=[],regions=[],result={items:[],total:0},confidence;
 if(view==='country'){
  const core=await api('country-page?'+new URLSearchParams({region,country,page,search}));if(version!==generation)return;
  countries=core.countries;result=core.players;confidence=core.confidence;
 }else{
  const requests=[];
  requests.push(detailed?api('countries?'+new URLSearchParams({region,country:'all'})):isGlobal?Promise.resolve([]):api('countries?region=all&country=all'));
  requests.push(isGlobal?Promise.resolve([]):api('regions?country=all'));
  requests.push(isGlobal?api('players?'+new URLSearchParams({region:'all',country:'all',page,size:10,search,sort:sortKey,direction:sortDirection})):Promise.resolve({items:[],total:0}));
  requests.push(api('confidence?'+new URLSearchParams({region:detailed?region:'all',country:'all'})));
  [countries,regions,result,confidence]=await Promise.all(requests);if(version!==generation)return;
 }
 $('estimateToggle').checked=false;$('currentEstimateToggle').checked=false;countryMode='current';
 data={status,countries,regions,result,trends:{points:[],regionPoints:[],movers:[],snapshotCount:0},modelEvidence:{snapshotId:selectedAct,archiveOnly:true},confidence,countryActivity:null};
 countryNames=status.countries||{};regionNames=status.regions||{};$('lastUpdated').textContent=time(status.latestSnapshot?.[0]?.completed_at);$('message').textContent='';
 const progress=status.archiveProgress||{},allSources=Number(progress.completedSources||0),sources=Number(progress.totalSources||7);
 $('dataFreshness').textContent=`${allSources===sources?'Archived act complete':'Historical archive in progress'} · ${fmt(progress.pages||0)} pages · ${fmt(progress.players||0)} global players · ${allSources}/${sources} source ladders complete.`;
 renderConfidence(status,confidence);setScope();renderTables();
 $('timelineNote').textContent='An archived act is a single end-of-act leaderboard snapshot. Movement and hourly history are not available.';
 $('playerTableNote').textContent='Archived act standings · 10 players per page. Select a player to inspect the archived rating.';
 if(view==='global'){$('playerSummary').textContent='Global leaderboard · '+$('actFilter').selectedOptions[0]?.textContent;$('playerTableNote').textContent='Archived global rank · 10 players per page. Select a player for this act’s archived rating.';}
 if(previewCountry)showCountryPreview(previewCountry,false);
 globalThis.observatoryRegionData={view,region,summary:regions.find(r=>r.region===region)||null,boundary:null,archive:true};globalThis.dispatchEvent(new CustomEvent('observatory:region-data',{detail:globalThis.observatoryRegionData}));
 if(!document.documentElement.dataset.coreReadyMs)document.documentElement.dataset.coreReadyMs=String(Math.round(performance.now()));
}
async function refreshGlobalPage(version){
  const [status,result,confidence]=await Promise.all([api('status'),api('players?'+new URLSearchParams({region:'all',country:'all',page,size:10,search,sort:sortKey,direction:sortDirection})),api('confidence')]);if(version!==generation)return;
 if(confidence.snapshot_id!==status.latestSnapshot[0]?.id)throw new Error('Snapshot changed; refresh to load the latest data.');
 const lastPage=Math.max(1,Math.ceil(Number(result.total||0)/10));if(page>lastPage){page=lastPage;syncGlobalUrl(false);return refreshGlobalPage(version);}
 data={status,result,confidence,countries:[],regions:[],modelEvidence:{snapshotId:status.latestSnapshot[0]?.id},trends:{points:[],regionPoints:[],movers:[],snapshotCount:0}};countryNames=status.countries;regionNames=status.regions;
 $('lastUpdated').textContent=time(status.latestSnapshot[0]?.completed_at);$('message').textContent='';setScope();renderTables();renderConfidence(status,confidence);
}
async function refreshPlayerProfilePage(version){
 if(!requestedProfilePlayer||requestedProfilePlayer.length>300||!requestedProfilePlayer.includes('#'))throw new Error('Open a player profile from a valid Riot ID link.');
 const [status,confidence]=await Promise.all([api('status'),api('confidence?'+new URLSearchParams({region:requestedProfileRegion,country:'all'}))]);if(version!==generation)return;
 if(!status.latestSnapshot?.length)throw new Error('No completed snapshot is available for this player profile.');
 data={status,confidence,countries:[],regions:[],result:{items:[],total:0},trends:{points:[],regionPoints:[],movers:[],snapshotCount:0},modelEvidence:{snapshotId:status.latestSnapshot[0].id}};
 countryNames=status.countries||{};regionNames=status.regions||{};$('lastUpdated').textContent=time(status.latestSnapshot[0]?.completed_at);$('message').textContent='';
 renderConfidence(status,confidence);setProfileScope();await showSelectedPlayer(requestedProfilePlayer,requestedProfileRegion,false,false);
}
async function refreshTrendsPage(version){
 const status=await api('status');if(version!==generation)return;
 data={status,countries:[],regions:[],result:{items:[],total:0},trends:{points:[],regionPoints:[],movers:[],snapshotCount:0},modelEvidence:{snapshotId:status.latestSnapshot?.[0]?.id}};
 countryNames=status.countries||{};regionNames=status.regions||{};$('lastUpdated').textContent=time(status.latestSnapshot?.[0]?.completed_at);$('message').textContent='';renderConfidence(status,null);setTrendsScope();
}
async function refreshCountryPage(version){
 const [status,core]=await Promise.all([api('status'),api('country-page?'+new URLSearchParams({region,country,page,search}))]);
 if(version!==generation)return;
 if(core.snapshotId!==status.latestSnapshot[0]?.id)throw new Error('Snapshot changed while loading. Refresh to load the latest data.');
 const old=data,modelEvidence=old?.modelEvidence?.snapshotId===core.snapshotId?old.modelEvidence:{snapshotId:core.snapshotId};
 data={status,countries:core.countries,regions:[],result:core.players,trends:{points:[],regionPoints:[],movers:[],snapshotCount:0},modelEvidence,countryActivity:old?.countryActivity,confidence:core.confidence};
 countryNames=status.countries;regionNames=status.regions;$('message').textContent='';$('lastUpdated').textContent=time(status.latestSnapshot[0]?.completed_at);
 $('dataFreshness').textContent=`Published snapshot: ${time(status.latestSnapshot[0]?.completed_at)}. Time filters end at this snapshot.${status.running?' Extraction is in progress.':''}`;
 setScope();renderTables();renderConfidence(status,core.confidence);
 $('movers').innerHTML='<p class="subtle">Loading RR changes…</p>';
 if(!document.documentElement.dataset.coreReadyMs)document.documentElement.dataset.coreReadyMs=String(Math.round(performance.now()));
 const filter=new URLSearchParams({region,country});
 api('trends?'+filter+'&'+new URLSearchParams({minutes:trendMinutes,direction})).then(trends=>{if(version!==generation)return;data.trends=trends;renderTrends();}).catch(e=>{if(version===generation)$('movers').textContent=e.message;});
 api('boundary-signals?'+filter).then(boundary=>{if(version===generation)renderBoundaryWatch(boundary);}).catch(()=>{});
 if(($('estimateToggle').checked||$('currentEstimateToggle').checked)&&data.modelEvidence.policyVersion!==3){
   const evidence=await api('country-model');if(version!==generation)return;
   if(evidence.snapshotId!==core.snapshotId)throw new Error('Snapshot changed while loading estimates. Refresh to retry.');
   data.modelEvidence=evidence;renderTables();
 }
}
async function showHistory(player,shard){try{const rows=await api('history?'+new URLSearchParams({player,region:shard}));$('historySection').classList.remove('hidden');$('historyTitle').textContent=player+' · '+regionName(shard);$('history').innerHTML=rows.map(p=>`<tr><td>${esc(time(p.observed_at||p.source_updated_at))}</td><td>${esc(p.observation_source||p.source)}</td><td>${esc(regionName(p.region))}</td><td>${countryLabel(p.country)}</td><td>${rank(p.global_rank,p)}</td><td>${rank(p.regional_rank,p)}</td><td>${fmt(p.rr)}</td><td>${tierBadge(p.tier)}</td></tr>`).join('');drawLines(rows.filter(p=>!p.stats_ambiguous).map(p=>({...p,observed:p.observed_at||p.source_updated_at})),'historyChart',[{key:'rr',color:'#73c9c4'}],'observed');$('historySection').scrollIntoView({behavior:'smooth'});}catch(e){$('message').textContent=e.message;}}
function changeScope(){page=1;countryPage=1;worldTrendCountry=null;resetMainPlayer();$('historySection').classList.add('hidden');refresh();}

function moverTable(rows,count,mode){return rows.length?'<table><thead><tr><th>Player</th><th>Country</th><th>Previous RR</th><th>Current RR</th><th>Δ RR</th></tr></thead><tbody>'+rows.slice(0,10).map(p=>`<tr><td><button data-player="${esc(p.player_key)}" data-region="${esc(p.region)}">${playerIdentity(p.display_name)}</button></td><td>${countryLabel(p.country)}</td><td title="${esc(time(p.previous_observed_at))}">${fmt(p.previous_rr)}</td><td title="${esc(time(p.current_observed_at))}">${fmt(p.current_rr)}</td><td>${delta(p.rr_change)}</td></tr>`).join('')+'</tbody></table>':`<p class="empty-state">${count<2?'Not enough observations in this window.':'No comparable '+(mode==='overall'?'RR changes':mode==='increment'?'RR increases':'RR decreases')+' in this window.'}</p>`;}
let mainSelection=null,previewSelection=null,overviewSelection=null,mainHistoryVersion=0,previewHistoryVersion=0,overviewHistoryVersion=0;
const historyVersion=prefix=>prefix==='overviewPlayer'?overviewHistoryVersion:prefix==='previewPlayer'?previewHistoryVersion:mainHistoryVersion;
async function showSelectedPlayer(player,shard,preview=false,scroll=true,overview=false){
 const playerLoadStarted=performance.now();
 const selection={player,region:shard};if(overview){overviewSelection=selection;$('overviewPlayerSection').classList.remove('hidden');if(!playerDialog.open){playerDialog.showModal();document.body.classList.add('player-dialog-open');}}else if(preview)previewSelection=selection;else mainSelection=selection;
 const token=overview?++overviewHistoryVersion:preview?++previewHistoryVersion:++mainHistoryVersion,prefix=overview?'overviewPlayer':preview?'previewPlayer':'selectedPlayer',minutes=overview?Number($('overviewPlayerWindow').value):preview?Number($('previewWindow').value):view==='profile'?Number($('profileWindow').value):trendMinutes;
 $(prefix+'Title').textContent=player+' · RR history';$(prefix+'Note').textContent='Loading source observations…';$(prefix+'Chart').classList.add('hidden');$(prefix+'Details').innerHTML='';$(prefix+'Profile').innerHTML='';document.getElementById(prefix+'LiveRating')?.remove();
 const oldRace=$(prefix+'Race');if(oldRace)oldRace.remove();document.getElementById(prefix+'Targets')?.remove();
 try{const archiveOnly=Boolean(data?.status?.archiveOnly),playerRegion=archiveOnly&&view==='global'?'all':shard;const [rows,summary,race,targets,journey]=await Promise.all([api('history?'+new URLSearchParams({player,region:playerRegion})),api('player-summary?'+new URLSearchParams({player,region:playerRegion})),archiveOnly?Promise.resolve(null):api('radiant-race?'+new URLSearchParams({player,region:shard,minutes})).catch(()=>null),archiveOnly?Promise.resolve(null):api('overtake-targets?'+new URLSearchParams({player,region:shard,minutes})).catch(()=>null),api('player-journey?'+new URLSearchParams({player,region:playerRegion})).catch(()=>null)]);if(token!==historyVersion(prefix))return;
 renderOvertakeTargets(targets,prefix,preview,overview);
 const end=new Date(data.status.latestSnapshot[0]?.completed_at).getTime(),start=end-minutes*60000;
 renderPlayerDetails(summary,prefix);renderPlayerJourney(journey,prefix);if(view==='profile'){const current=summary.current?.[0];$('playerProfileHeading').textContent=current?.display_name||player;$('playerProfileMeta').textContent=[regionName(current?.region||shard),current?.country?countryName(current.country):'Unknown country',summary.act?`${summary.act===currentAct?'Current act':'Archived act'} · ${summary.act}`:'Current act',current?.source_updated_at?`Stats observed ${time(current.source_updated_at)}`:'No current player observation',current?.regional_rank_updated_at?`Regional rank observed ${time(current.regional_rank_updated_at)}`:null].filter(Boolean).join(' · ');}if(!archiveOnly)loadTrackerProfile(summary.current[0]?.display_name||player,prefix,token);else $(prefix+'Profile').innerHTML='<p class="subtle">Tracker profile and live competition data are not available for an archived Act. The source snapshot and journey remain available.</p>';
 const allPoints=rows.filter(p=>!p.stats_ambiguous&&!p.carried_forward).map(p=>({...p,observed:p.observed_at||p.source_updated_at}));const points=archiveOnly?allPoints:allPoints.filter(p=>{const t=new Date(p.observed).getTime();return t>=start&&t<=end;});
 if(view==='profile'){$('selectedPlayerHistoryRows').innerHTML=points.length?points.slice().sort((a,b)=>new Date(a.observed)-new Date(b.observed)).map(p=>`<tr><td>${esc(time(p.observed))}</td><td>${esc(regionName(p.region))}</td><td>${countryLabel(p.country)}</td><td>${fmt(p.global_rank)}</td><td>${fmt(p.regional_rank)}</td><td>${fmt(p.rr)}</td><td>${esc(p.tier)}</td></tr>`).join(''):'<tr><td colspan="7">No usable source observations are available in this window.</td></tr>';}
 $(prefix+'Note').textContent=archiveOnly?(points.length?'Archived act snapshot · RR at archived source time':'No archived observation for this player in this act.'):points.length?`${minutes} min · ${points.length} source observation${points.length===1?'':'s'} · RR at source time`:'No source observations for this player in the selected time range.';
 if(points.length){$(prefix+'Chart').classList.remove('hidden');drawLines(points,prefix+'Chart',[{key:'rr',color:'#73c9c4'}],'observed',true);}
 if(!archiveOnly&&renderRadiantRace(race,prefix))$(prefix+'Note').textContent=`${minutes} min · RR and observed Radiant boundary at completed snapshots · source times shown in player details`;
 $(prefix+'Note').dataset.readyMs=String(Math.round(performance.now()-playerLoadStarted));
 if(scroll&&(overview||globalThis.matchMedia?.('(max-width:950px)').matches))$(prefix+'Title').scrollIntoView({behavior:'smooth',block:'start'});
 }catch(e){if(token===historyVersion(prefix))$(prefix+'Note').textContent=e.message;}
}
function renderPlayerJourney(payload,prefix){
 const box=$(prefix+'Journey');if(!box)return;const items=payload?.items||[];if(!items.length){box.innerHTML='<h3>Player journey · current and previous acts</h3><p class="subtle">No archived or current snapshots are available for this player yet.</p>';return;}
 const regions={ap:'Asia-Pacific',eu:'Europe',na:'North America',latam:'Latin America',br:'Brazil',kr:'South Korea'},values=items.map(x=>Number(x.rr)).filter(Number.isFinite),first=values[0],last=values.at(-1),best=Math.max(...values);
 const canvasId=prefix+'JourneyChart';box.innerHTML=`<h3>Player journey · ${fmt(items.length)} act snapshots</h3><p class="subtle">End-of-act archive snapshots plus the latest complete current snapshot. RR changes between points are not match-by-match history. Combined Immortal tiers are estimated from regional RR thresholds.</p><canvas id="${canvasId}" class="player-journey-chart" role="img" aria-label="Player ranked rating across acts"></canvas><div class="act-trends-scroll"><table><thead><tr><th>Act</th><th>Region</th><th>Country</th><th>Tier</th><th>RR</th><th>Regional rank</th><th>Global rank</th><th>Wins</th><th>Observed</th></tr></thead><tbody>${items.map(x=>`<tr${x.current_act?' class="current-journey-row"':''}><td>${esc(x.label)}${x.current_act?' · current':''}</td><td>${esc(regions[x.region]||x.region)}</td><td>${esc(countryName(x.country))}</td><td>${esc(x.interpreted_tier||x.source_tier)}</td><td>${fmt(x.rr)}</td><td>${fmt(x.regional_rank)}</td><td>${fmt(x.global_rank)}</td><td>${fmt(x.wins)}</td><td>${esc(time(x.source_updated_at))}</td></tr>`).join('')}</tbody></table></div><p class="subtle">Observed RR path: ${fmt(first)} → ${fmt(last)} · peak in this archive: ${fmt(best)} RR. ${esc(payload.tierNote||'')}</p>`;
 const canvas=$(canvasId),ctx=canvas.getContext('2d'),width=Math.max(280,canvas.clientWidth||600),height=180,ratio=devicePixelRatio||1;canvas.width=width*ratio;canvas.height=height*ratio;ctx.scale(ratio,ratio);ctx.clearRect(0,0,width,height);const left=38,right=12,top=14,bottom=30,min=Math.min(...values),max=Math.max(...values),span=Math.max(1,max-min),step=(width-left-right)/Math.max(1,items.length-1);ctx.strokeStyle='#293c50';ctx.fillStyle='#9db2c8';ctx.font='10px system-ui';for(let i=0;i<4;i++){const y=top+(height-top-bottom)*i/3;ctx.beginPath();ctx.moveTo(left,y);ctx.lineTo(width-right,y);ctx.stroke();ctx.fillText(fmt(Math.round(max-span*i/3)),2,y+3)}ctx.strokeStyle='#73c9c4';ctx.lineWidth=2;ctx.beginPath();items.forEach((x,i)=>{const px=left+step*i,py=top+(height-top-bottom)*(max-Number(x.rr||0))/span;i?ctx.lineTo(px,py):ctx.moveTo(px,py)});ctx.stroke();ctx.fillStyle='#73c9c4';items.forEach((x,i)=>{const px=left+step*i,py=top+(height-top-bottom)*(max-Number(x.rr||0))/span;ctx.beginPath();ctx.arc(px,py,2.5,0,Math.PI*2);ctx.fill()});ctx.fillStyle='#9db2c8';ctx.textAlign='left';ctx.fillText(items[0].label||'',left,height-6);ctx.textAlign='right';ctx.fillText(items.at(-1).label||'',width-right,height-6);
}
const profileCache=new Map();
async function loadTrackerProfile(player,prefix,token){
 const container=$(prefix+'Profile');container.innerHTML='<p class="subtle">Loading Tracker profile…</p>';
 try{const key=player.toLowerCase();let entry=profileCache.get(key);if(!entry||entry.expires<Date.now()){entry={expires:Date.now()+900000,promise:api('profile?'+new URLSearchParams({player}))};profileCache.set(key,entry);if(profileCache.size>256)profileCache.delete(profileCache.keys().next().value);}
 const profile=await entry.promise;if(token!==historyVersion(prefix))return;
 if(!profile.available){entry.expires=Math.min(entry.expires,Date.now()+300000);container.innerHTML='<p class="subtle">Tracker profile unavailable. The snapshot and best-observed values above remain available.</p>';return;}
 const current=profile.current,peak=profile.peak;
 if(current.tier){
   let live=document.getElementById(prefix+'LiveRating');if(!live){live=document.createElement('div');live.id=prefix+'LiveRating';live.className='player-details';$(prefix+'Details').before(live);}
   live.innerHTML=`<h3>Latest Tracker rating</h3><p>${tierBadge(current.tier)} <strong>${esc(current.tier)} · ${fmt(current.rr)} RR</strong></p><p class="subtle">Profile fetched ${esc(time(profile.fetchedAt))} · cached up to 15 minutes. The published snapshot and history below keep their original observation times.</p>`;
 }
 container.innerHTML=(profile.avatarUrl?`<img class="profile-avatar" src="${esc(profile.avatarUrl)}" alt="Player avatar" referrerpolicy="no-referrer">`:'')+'<h3>Tracker profile</h3><dl class="player-facts">'+(current.tier?`<div><dt>Current act rating</dt><dd>${tierBadge(current.tier)} ${fmt(current.rr)} RR</dd></div><div><dt>Profile position</dt><dd>${current.position?'#'+fmt(current.position):'—'}</dd></div>`:'')+(peak.tier?`<div><dt>Tracker peak rating</dt><dd>${tierBadge(peak.tier)} ${fmt(peak.rr)} RR</dd></div><div><dt>Peak act</dt><dd>${esc(peak.actName||peak.actId||'Not specified')}</dd></div>`:'')+'</dl>'+`<p class="subtle">${esc(profile.shard?.toUpperCase()||'')} · Tracker profile fetched ${esc(time(profile.fetchedAt))}. Cached up to 15 min. Profile values are separate from the completed snapshot and RR graph.</p>`;
 }catch(e){profileCache.delete(player.toLowerCase());if(token===historyVersion(prefix))container.innerHTML='<p class="subtle">Tracker profile unavailable. The snapshot values above remain available.</p>';}
}
function renderPlayerDetails(summary,prefix){
 const current=summary.current[0],best=summary.best,peak=summary.peak[0],display=current?.display_name||$(prefix+'Title').textContent.split(' · ')[0];
 $(prefix+'Title').textContent=display+' · RR history';
 const facts=[['Published tier',current?tierBadge(current.tier):'Not in latest snapshot'],['Published RR',fmt(current?.rr)],['Country',current?countryLabel(current.country):'—'],['Region',current?esc(regionName(current.region)):'—'],['Recorded act wins',fmt(current?.wins)],['Published global rank',current?rank(current.global_rank,current):'—'],['Published regional rank',current?rank(current.regional_rank,current):'—'],['Best observed tier',peak?tierBadge(peak.tier):'—'],['Best observed RR',fmt(best.best_rr)],['Best global rank',fmt(best.best_global_rank)],['Best regional rank',fmt(best.best_regional_rank)]];
 $(prefix+'Details').innerHTML=playerIdentity(display)+'<h3>Player snapshot</h3><dl class="player-facts">'+facts.map(([label,value])=>`<div><dt>${label}</dt><dd>${value}</dd></div>`).join('')+'</dl>'+`<p class="subtle">${current?.carried_forward?'Older carried observation · ':''}${current?.stats_ambiguous?'Conflicting statistics · ':''}Stats as of ${esc(time(current?.source_updated_at))}${current?.regional_rank_updated_at?' · Regional rank as of '+esc(time(current.regional_rank_updated_at)):''}</p><p class="subtle">Best values cover ${fmt(best.observation_count)} world-snapshot observations retained in this act. Best values exclude carried copies and conflicting observations, may come from different snapshots, and are not an all-time account peak.</p><a class="page-link" href="https://tracker.gg/valorant/profile/riot/${encodeURIComponent(display)}/overview?platform=pc&playlist=competitive&season=${encodeURIComponent(summary.act)}" target="_blank" rel="noopener noreferrer">Open Tracker profile ↗</a>`;
}
function resetMainPlayer(){document.getElementById('selectedPlayerLiveRating')?.remove();document.getElementById('selectedPlayerTargets')?.remove();document.getElementById('selectedPlayerRace')?.remove();mainSelection=null;mainHistoryVersion++;$('selectedPlayerTitle').textContent='Player RR history';$('selectedPlayerNote').textContent='';$('selectedPlayerChart').classList.add('hidden');$('selectedPlayerDetails').innerHTML='';$('selectedPlayerJourney').innerHTML='';$('selectedPlayerProfile').innerHTML='';}
function resetPreviewPlayer(){document.getElementById('previewPlayerLiveRating')?.remove();document.getElementById('previewPlayerTargets')?.remove();document.getElementById('previewPlayerRace')?.remove();previewSelection=null;previewHistoryVersion++;$('previewPlayerTitle').textContent='Player RR history';$('previewPlayerNote').textContent='';$('previewPlayerChart').classList.add('hidden');$('previewPlayerDetails').innerHTML='';$('previewPlayerJourney').innerHTML='';$('previewPlayerProfile').innerHTML='';}
async function showCountryPreview(selected,scroll=true){
 if(previewCountry!==selected)resetPreviewPlayer();previewCountry=selected;const token=++previewGeneration;
 if(view==='world'&&!data.status.archiveOnly){worldTrendCountry=selected;const trends=await api('trends?'+new URLSearchParams({region,country:selected,minutes:trendMinutes,direction}));if(token!==previewGeneration)return;data.trends=trends;renderTrends();}
 try{const result=await api('players?'+new URLSearchParams({region,country:selected,page:1,size:10}));if(token!==previewGeneration)return;
 renderTables();$('countryPreview').classList.remove('hidden');$('previewTitle').textContent=countryName(selected)+' · Country preview';$('previewPage').href=pageUrl('country',region,selected);
 $('previewWindowNote').textContent=data.status.archiveOnly?'Archived act standings · Reported country · ordered by global rank':'Reported-country filter · Unknown country excluded · ordered by global leaderboard rank';
 $('previewPlayers').innerHTML=result.items.length?'<table><thead><tr><th>Player</th><th>Global rank</th><th>Regional rank</th><th>Tier</th><th>RR</th></tr></thead><tbody>'+result.items.map(p=>`<tr><td><button data-player="${esc(p.player_key)}" data-region="${esc(p.region)}">${playerIdentity(p.display_name)}</button></td><td>${rank(p.global_rank,p)}</td><td>${rank(p.regional_rank,p)}</td><td>${tierBadge(p.tier)}</td><td>${fmt(p.rr)}</td></tr>`).join('')+'</tbody></table>':'<p class="empty-state">No players in the latest complete snapshot.</p>';
 if(previewSelection)showSelectedPlayer(previewSelection.player,previewSelection.region,true,false);
 if(scroll)$('countryPreview').scrollIntoView({behavior:globalThis.matchMedia?.('(prefers-reduced-motion:reduce)').matches?'instant':'smooth',block:'start'});
 }catch(e){$('message').textContent=e.message;}
}
$('previewClose').onclick=()=>{const selected=previewCountry;previewCountry=null;previewGeneration++;resetPreviewPlayer();$('countryPreview').classList.add('hidden');renderTables();$('countries').querySelector?.('button[data-country="'+selected+'"]')?.focus({preventScroll:true});$('countrySection').scrollIntoView({behavior:globalThis.matchMedia?.('(prefers-reduced-motion:reduce)').matches?'instant':'smooth',block:'nearest'});};
const countryClick=e=>{const count=e.target.closest('[data-boundary-signal]');if(count){openBoundaryPlayers(count.dataset.boundaryCountry,count.dataset.boundarySignal);return;}const b=e.target.closest('[data-country]');if(b)showCountryPreview(b.dataset.country);};$('countries').onclick=countryClick;$('unknownCountry').onclick=countryClick;
$('previewWindow').onchange=()=>{if(previewCountry)showCountryPreview(previewCountry,false);};
async function changeEstimateSetting(source){
 const enabled=source.checked;$('estimateToggle').checked=enabled;$('currentEstimateToggle').checked=enabled;
 if(enabled&&data?.modelEvidence?.policyVersion!==3){source.disabled=true;$('message').textContent='Loading country estimates; observed data remains available…';try{await refresh();if(data?.modelEvidence?.policyVersion!==3){$('estimateToggle').checked=false;$('currentEstimateToggle').checked=false;renderTables();}}finally{source.disabled=false;}}
 else renderTables();
}
$('estimateToggle').onchange=()=>changeEstimateSetting($('estimateToggle'));
$('countryDataMode').onchange=()=>{countryMode=$('countryDataMode').value;countryPage=1;countrySort.key=countryMode==='movement'?'active':'players';countrySort.dir='desc';renderTables();};
$('countryMovementWindow').onchange=()=>{activityMinutes=Number($('countryMovementWindow').value);loadCountryActivity();};
$('countryActivityWindow').onchange=()=>{activityMinutes=Number($('countryActivityWindow').value);countryPage=1;loadCountryActivity();};
$('currentEstimateToggle').onchange=()=>{if(data?.status?.archiveOnly){renderCurrent();return;}changeEstimateSetting($('currentEstimateToggle'));};
$('regions').onclick=e=>{const b=e.target.closest('[data-region]');if(b){if($('standingsDialog').open){location.href=pageUrl('region',b.dataset.region);return;}region=region===b.dataset.region?'all':b.dataset.region;country='all';previewCountry=null;previewGeneration++;$('countryPreview').classList.add('hidden');changeScope();}};
$('players').onclick=async e=>{const b=e.target.closest('[data-player]');if(b){if(view==='global'){location.href=playerProfileUrl(b.dataset.player,b.dataset.region);return;}await showSelectedPlayer(b.dataset.player,b.dataset.region);$('selectedPlayerSection').scrollIntoView({behavior:'smooth',block:'start'});}};
$('globalPlayerCards').onclick=e=>{const b=e.target.closest('[data-player]');if(b)location.href=playerProfileUrl(b.dataset.player,b.dataset.region);};
for(const id of ['movers','previewPlayers'])$(id).onclick=e=>{const b=e.target.closest('[data-player]');if(b)showSelectedPlayer(b.dataset.player,b.dataset.region,id==='previewPlayers');};
for(const [id,sort] of [['regionHead',regionSort],['countryHead',countrySort]])$(id).onclick=e=>{const b=e.target.closest('[data-sort]');if(b){sort.dir=sort.key===b.dataset.sort&&sort.dir==='desc'?'asc':'desc';sort.key=b.dataset.sort;if(id==='countryHead')countryPage=1;renderTables();}};
$('countryFirst').onclick=()=>{countryPage=1;renderTables();};$('countryPrev').onclick=()=>{countryPage=Math.max(1,countryPage-1);renderTables();};$('countryNext').onclick=()=>{countryPage=Math.min(countryPages,countryPage+1);renderTables();};$('countryLast').onclick=()=>{countryPage=countryPages;renderTables();};$('countryJump').onchange=()=>{countryPage=Math.min(countryPages,Math.max(1,Number($('countryJump').value)||1));renderTables();};
function playerPage(n){page=Math.min(playerPages,Math.max(1,n));if(view==='global')syncGlobalUrl(true);refresh();}$('playerFirst').onclick=()=>playerPage(1);$('prev').onclick=()=>playerPage(page-1);$('next').onclick=()=>playerPage(page+1);$('playerLast').onclick=()=>playerPage(playerPages);$('playerJump').onchange=()=>playerPage(Number($('playerJump').value)||1);
let searchTimer;$('playerSearch').oninput=()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{search=$('playerSearch').value;page=1;if(view==='global')syncGlobalUrl(false);refresh();},300);};
window.addEventListener('popstate',()=>{if(view!=='global')return;const p=new URLSearchParams(location.search);search=p.get('q')||p.get('search')||'';page=Math.max(1,Math.floor(Number(p.get('page')||1)));sortKey=playerSorts.has(p.get('sort'))?p.get('sort'):'globalRank';sortDirection=p.get('direction')==='desc'?'desc':'asc';$('playerSearch').value=search;$('globalSort').value=sortKey+':'+sortDirection;refresh();});
$('globalSort').onchange=()=>{const [key,dir]=$('globalSort').value.split(':');sortKey=key;sortDirection=dir;page=1;syncGlobalUrl(false);refresh();};
$('trendWindow').onchange=()=>{trendMinutes=Number($('trendWindow').value);refresh();};$('movementDirection').onchange=()=>{direction=$('movementDirection').value;refresh();};$('moverCountry').onchange=()=>{moverCountry=$('moverCountry').value;resetMainPlayer();refresh();};$('chartMode').onchange=()=>{if(data)renderTrends();};$('refresh').onclick=refresh;
globalThis.addEventListener?.('resize',()=>{if(data)renderTrends();});
let authMode='signin',authReturnFocus=null;
function authModeSet(mode){authMode=mode;const signup=mode==='signup';$('authTitle').textContent=signup?'Create your account':'Welcome back';$('authSubmit').textContent=signup?'Create account':'Sign in';$('registerFields').classList.toggle('hidden',!signup);$('loginLabel').classList.toggle('hidden',signup);$('authLogin').disabled=signup;$('authLogin').required=!signup;
 for(const id of ['authUsername','authName','authEmail']){$(id).disabled=!signup;$(id).required=signup;}
 $('authPassword').minLength=signup?8:1;$('authPassword').autocomplete=signup?'new-password':'current-password';$('authMessage').textContent='';}
$('accountControls').onclick=e=>{const link=e.target.closest('a');if(link&&['/signin.html','/signup.html'].includes(link.getAttribute('href'))){e.preventDefault();authReturnFocus=link;authModeSet(link.getAttribute('href').includes('signup')?'signup':'signin');$('authDialog').showModal();(authMode==='signup'?$('authUsername'):$('authLogin')).focus();}};
$('authSignin').onclick=()=>authModeSet('signin');$('authSignup').onclick=()=>authModeSet('signup');$('authClose').onclick=()=>$('authDialog').close();
$('authDialog').onclose=()=>{$('authPassword').value='';authReturnFocus?.focus();};
$('authDialog').onclick=e=>{if(e.target===$('authDialog')){const bounds=$('authDialog').getBoundingClientRect();if(e.clientX<bounds.left||e.clientX>bounds.right||e.clientY<bounds.top||e.clientY>bounds.bottom)$('authDialog').close();}};
$('authForm').onsubmit=async e=>{e.preventDefault();$('authSubmit').disabled=true;$('authMessage').textContent='';
 try{const csrf=await api('csrf');if(authMode==='signup'){
 const response=await fetch('/api/account/register',{method:'POST',headers:{'Content-Type':'application/json',[csrf.header]:csrf.token},body:JSON.stringify({username:$('authUsername').value,name:$('authName').value,email:$('authEmail').value,password:$('authPassword').value})});
 if(!response.ok){let message={};try{message=await response.json();}catch(ignored){}throw new Error(message.message||'Unable to create account. Check your details and try again.');}
 $('authLogin').value=$('authUsername').value;authModeSet('signin');$('authPassword').value='';$('authMessage').textContent='Account created. Sign in to continue.';$('authPassword').focus();
 }else{
 const response=await fetch('/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',[csrf.header]:csrf.token},body:new URLSearchParams({username:$('authLogin').value,password:$('authPassword').value})});
 if(!response.ok||new URL(response.url,location.href).searchParams.has('error'))throw new Error('The username/email or password was not recognized.');
 const user=await(await fetch('/api/account/me')).json();if(!user.authenticated)throw new Error('Unable to sign in. Please try again.');
 await accountHeader();$('authDialog').close();
 }}catch(error){$('authMessage').textContent=error.message;}finally{$('authSubmit').disabled=false;}
};
async function accountHeader(){try{const r=await fetch('/api/account/me');if(!r.ok)return;const user=await r.json();if(!user.authenticated)return;$('accountControls').innerHTML=`<span class="account-avatar" aria-hidden="true">${esc(Array.from(user.name||user.username)[0].toUpperCase())}</span><span class="viewer-name" title="${esc(user.name)}">${esc(user.name)}</span><a href="/settings" class="account-settings-link">Settings</a><button id="signOut">Sign out</button>`;$('signOut').onclick=async()=>{try{const csrf=await api('csrf');const result=await fetch('/logout',{method:'POST',headers:{[csrf.header]:csrf.token}});if(!result.ok)throw new Error('Unable to sign out. Refresh and try again.');location.href='/leaderboard.html';}catch(error){$('message').textContent=error.message;}};}catch(ignored){}}
$('subregionFilter').onchange=()=>{subregion=$('subregionFilter').value;countryPage=1;previewCountry=null;$('countryPreview').classList.add('hidden');renderTables();};
const standingsDialog=$('standingsDialog');
if(view==='world')standingsDialog.appendChild($('regionTableSection'));
$('openStandings').onclick=()=>standingsDialog.showModal();
$('closeStandings').onclick=()=>standingsDialog.close();
standingsDialog.addEventListener('click',e=>{if(e.target===standingsDialog){const r=standingsDialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)standingsDialog.close();}});
$('archivePageWorkspace').addEventListener('click',event=>{
 const tab=event.target.closest('[data-archive-tab]');if(tab){archiveTab=tab.dataset.archiveTab;archivePage=1;renderArchiveExplorer();return;}
 const pageButton=event.target.closest('[data-archive-page]');if(pageButton){archivePage=Math.max(1,archivePage+(pageButton.dataset.archivePage==='next'?1:-1));refresh();return;}
 if(event.target.closest('#archiveRetry'))refresh();
});
$('archivePageWorkspace').addEventListener('change',event=>{
 if(event.target.matches('[data-archive-region]')){archiveRegion=event.target.value;archivePage=1;refresh();}
 if(event.target.matches('[data-archive-country]')){archiveCountry=event.target.value;archivePage=1;refresh();}
});
$('archivePageWorkspace').addEventListener('input',event=>{if(event.target.matches('[data-archive-search]')){archiveSearch=event.target.value;archivePage=1;clearTimeout(archiveSearchTimer);archiveSearchTimer=setTimeout(refresh,300);}});
accountHeader();
initializeActFilter().then(()=>refresh()).catch(error=>{$('message').textContent=error.message;});setInterval(()=>{if(selectedAct&&!document.hidden)refresh();},60000);
