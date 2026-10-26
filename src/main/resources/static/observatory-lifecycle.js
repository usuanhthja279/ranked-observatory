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
  const api=Object.freeze({createLatestRequest});
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.ObservatoryLifecycle=api;
})(globalThis);
