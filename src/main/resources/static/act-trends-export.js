'use strict';
(()=>{
 function buildActTrendCsv(headers,rows){
  const cell=value=>{
   let text=String(value??'');
   if(typeof value==='string'&&/^\s*[=+\-@]/.test(text))text="'"+text;
   return '"'+text.replace(/"/g,'""')+'"';
  };
  return '\ufeff'+[headers,...rows].map(row=>row.map(cell).join(',')).join('\r\n')+'\r\n';
 }
 globalThis.buildActTrendCsv=buildActTrendCsv;
 if(typeof module!=='undefined'&&module.exports)module.exports=buildActTrendCsv;
})();
