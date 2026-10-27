(function(root){
  function createLatestRequest(){
    let generation=0,controller=null;
    return Object.freeze({
      begin(){
        controller?.abort();
        controller=new AbortController();
        const ownController=controller,id=++generation;
        return Object.freeze({signal:ownController.signal,isCurrent:()=>id===generation&&!ownController.signal.aborted});
      },
      cancel(){
        generation++;
        controller?.abort();
        controller=null;
      }
    });
  }
  function runWhenDisclosureOpens(disclosure,callback,eager=false){
    let started=false;
    const start=()=>{
      if(started)return;
      started=true;
      disclosure?.removeEventListener?.('toggle',onToggle);
      callback();
    };
    const onToggle=()=>{if(disclosure?.open)start()};
    if(eager||disclosure?.open)start();
    else disclosure?.addEventListener?.('toggle',onToggle);
    return start;
  }
  const api=Object.freeze({createLatestRequest,runWhenDisclosureOpens});
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.ObservatoryLifecycle=api;
})(globalThis);
