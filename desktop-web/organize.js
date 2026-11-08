'use strict';
(() => {
  const main=document.querySelector('main'), library=document.createElement('div');
  while(main.firstChild)library.append(main.firstChild);
  main.append(library);
  const panel=document.createElement('section');panel.id='organizer';panel.hidden=true;
  panel.innerHTML='<header><h1>Organizar</h1><button id="org-refresh">Atualizar</button></header><p>Análise no celular · sem nuvem. Apenas fotos, não vídeos.</p><p id="org-status" role="status"></p><div class="org-controls"><button id="org-scan" class="primary">Analisar fotos</button><button id="org-pause">Pausar</button><label><input id="org-charging" type="checkbox"> Analisar somente carregando</label></div><p class="org-help">Mantenha a conexão aberta no celular. Fechar esta aba não interrompe a análise; use Pausar. Encerrar a conexão no celular interrompe a análise e preserva os resultados.</p><div class="org-controls"><button id="org-people">Pessoas</button><button id="org-similar">Fotos semelhantes</button></div><div id="org-content" class="org-scroll" tabindex="0" aria-label="Resultados da organização"></div>';
  main.append(panel);
  const mergeDialog=document.createElement('dialog');mergeDialog.id='org-merge';document.body.append(mergeDialog);
  let state={people:[],similar:[],running:false}, tab='people', person=null, busy=false, loading=false;
  const el=(tag,text,cls)=>{const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(cls)node.className=cls;return node;};
  const button=(text,fn,disabled=false)=>{const b=el('button',text);b.onclick=fn;b.disabled=disabled;return b;};
  const preview=(src,alt)=>{const img=el('img');img.src=src;img.alt=alt;img.loading='lazy';return img;};
  const faceUrl=f=>'/api/organize/face/'+encodeURIComponent(f.key);
  function photo(id){const item=items.find(x=>x.id===id);const b=button(' ',()=>show(id),!item);b.className='org-photo';b.replaceChildren(preview('/api/media/'+encodeURIComponent(id)+'/thumb',item?.name || 'Foto'));return b;}
  async function reload(){
    if(loading||panel.hidden||!window.poetrateRemote)return;
    loading=true;
    try{const next=await window.poetrateRemote.api('/api/organize');if(JSON.stringify(next)!==JSON.stringify(state)){state=next;renderOrganization();}}
    catch(e){$('org-status').textContent=e.message;}
    finally{loading=false;}
  }
  async function act(action,data={}){
    if(busy)return;busy=true;renderOrganization();
    try{await window.poetrateRemote.api('/api/organize/'+action,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});await reload();return true;}
    catch(e){toast(e.message);return false;}
    finally{busy=false;renderOrganization();}
  }
  function mergeGroups(group){
    const chosen=new Set();mergeDialog.replaceChildren();
    const heading=el('h2','Unir vários grupos');const note=el('p','Marque os grupos da mesma pessoa. O grupo “'+group.name+'” e seu nome serão mantidos.');
    const list=el('div',undefined,'org-scroll org-merge-list');list.tabIndex=0;list.setAttribute('aria-label','Grupos para unir');
    const confirmButton=button('Unir selecionados',async()=>{
      if(!chosen.size||busy)return;
      confirmButton.disabled=true;
      if(await act('merge',{ids:[...chosen],into:group.id}))mergeDialog.close();else update();
    },true);
    const checks=[];
    const update=()=>{confirmButton.disabled=!chosen.size;confirmButton.textContent='Unir '+chosen.size+' selecionados';};
    state.people.filter(p=>p.id!==group.id).forEach(p=>{
      const row=el('label',undefined,'org-merge-row'), check=el('input');check.type='checkbox';check.setAttribute('aria-label','Selecionar '+p.name);
      check.onchange=()=>{check.checked?chosen.add(p.id):chosen.delete(p.id);update();};checks.push({check,id:p.id});
      row.append(check,preview(faceUrl(p.faces[0]),p.name),el('span',p.name+' · '+new Set(p.faces.map(f=>f.mediaId)).size+' fotos'));list.append(row);
    });
    const all=button('Selecionar / desmarcar todos',()=>{const on=chosen.size!==checks.length;chosen.clear();checks.forEach(({check,id})=>{check.checked=on;if(on)chosen.add(id);});update();});
    const actions=el('div',undefined,'org-controls');actions.append(confirmButton,button('Cancelar',()=>mergeDialog.close()));mergeDialog.append(heading,note,all,list,actions);mergeDialog.showModal();
  }
  function renderOrganization(){
    $('org-status').textContent=state.status || 'Conecte ao celular para analisar.';
    $('org-scan').disabled=busy||state.running;$('org-pause').disabled=busy||!state.running;$('org-charging').checked=!!state.charging;$('org-charging').disabled=busy;
    $('org-people').classList.toggle('active',tab==='people');$('org-similar').classList.toggle('active',tab==='similar');
    const content=$('org-content'), scroll=content.scrollTop;const horizontal=new Map([...content.querySelectorAll('.org-photo-row')].map(row=>[row.dataset.key,row.scrollLeft]));content.replaceChildren();
    if(tab==='people'){
      const group=state.people.find(p=>p.id===person);
      if(!group){
        const grid=el('div',undefined,'org-people-grid');
        state.people.forEach(p=>{const b=button('',()=>{person=p.id;content.scrollTop=0;renderOrganization();});b.className='org-person';b.append(preview(faceUrl(p.faces[0]),p.name),el('strong',p.name),el('span',new Set(p.faces.map(f=>f.mediaId)).size+' fotos'));grid.append(b);});content.append(grid);
        if(!state.people.length)content.append(el('p','Nenhuma pessoa encontrada ainda. Inicie a análise acima.'));
      }else{
        content.append(button('← Todas as pessoas',()=>{person=null;content.scrollTop=0;renderOrganization();}),el('h2',group.name));
        const controls=el('div',undefined,'org-controls');controls.append(button('Dar nome',()=>{const name=prompt('Nome da pessoa',group.name);if(name!==null)act('rename',{id:group.id,name});},busy||state.running),button('Unir grupos',()=>mergeGroups(group),busy||state.running||state.people.length<2));content.append(controls);
        const grid=el('div',undefined,'org-people-grid');
        group.faces.forEach(f=>{const card=el('article',undefined,'org-card');const crop=preview(faceUrl(f),'Rosto deste grupo');crop.className='org-face';card.append(crop,photo(f.mediaId),button('Não é esta pessoa · separar',()=>{if(confirm('Separar este rosto em um novo grupo? A foto não será alterada.'))act('separate',{key:f.key});},busy||state.running));grid.append(card);});content.append(grid);
      }
    }else{
      content.append(el('p','Sugestões por aparência e composição. Abra as fotos para comparar. Nada é apagado automaticamente.'));
      state.similar.forEach(ids=>{const card=el('article',undefined,'org-card');card.append(el('h3',ids.length+' fotos semelhantes'));const row=el('div',undefined,'org-photo-row');row.dataset.key=ids[0];row.tabIndex=0;row.setAttribute('aria-label','Fotos deste conjunto');ids.forEach(id=>row.append(photo(id)));card.append(row);content.append(card);});
      if(!state.similar.length)content.append(el('p','Nenhum conjunto semelhante encontrado na análise atual.'));
    }
    content.scrollTop=scroll;content.querySelectorAll(".org-photo-row").forEach(row=>{row.scrollLeft=horizontal.get(row.dataset.key)||0;});
  }
  $('organize-nav').onclick=async()=>{
    if(!window.poetrateRemote){toast('Abra o endereço mostrado pelo Poetrate no celular para usar Organizar.');return;}
    library.hidden=true;panel.hidden=false;document.querySelectorAll('.nav').forEach(b=>b.classList.toggle('active',b.id==='organize-nav'));
    try{await window.poetrateRemote.refresh();await reload();}catch(e){$('org-status').textContent=e.message;}
  };
  document.querySelectorAll('[data-filter]').forEach(b=>b.addEventListener('click',()=>{library.hidden=false;panel.hidden=true;$('organize-nav').classList.remove('active');}));
  $('org-refresh').onclick=async()=>{try{await window.poetrateRemote.refresh();await reload();}catch(e){toast(e.message);}};
  $('org-scan').onclick=()=>act('scan');$('org-pause').onclick=()=>act('pause');$('org-charging').onchange=e=>act('charging',{value:e.target.checked});
  $('org-people').onclick=()=>{tab='people';person=null;$('org-content').scrollTop=0;renderOrganization();};$('org-similar').onclick=()=>{tab='similar';person=null;$('org-content').scrollTop=0;renderOrganization();};
  setInterval(()=>{if(!document.hidden&&!panel.hidden&&!mergeDialog.open&&!busy)reload();},4000);
})();
