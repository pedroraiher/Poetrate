'use strict';
if (location.protocol !== 'file:') {
  const connection = document.createElement('dialog');
  connection.innerHTML = '<form><h2>Conectar ao Poetrate</h2><p>Informe o código mostrado no celular e autorize o acesso nele.</p><label>Código <input name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required autocomplete="off"></label><p role="status"></p><button class="primary">Solicitar acesso</button></form>';
  document.body.append(connection);
  connection.addEventListener('cancel', e => e.preventDefault());
  const status = connection.querySelector('[role=status]');
  let busy = false, pairing = false;
  async function api(path, options = {}) {
    const response = await fetch(path, { ...options, cache: 'no-store', credentials: 'same-origin' });
    const data = await response.json();
    if (!response.ok) {
      if (response.status === 401 && !pairing) { $('viewer').close(); items = []; selected.clear(); render(); if (!connection.open) connection.showModal(); }
      throw new Error(data.error || 'Não foi possível concluir.');
    }
    return data;
  }
  async function refresh() {
    const palette = await api('/api/theme');
    for (const [key,value] of Object.entries(palette)) {
      if (['background','surface','container','outline','primary','on-primary','text','muted'].includes(key) && /^#[0-9a-f]{6}$/i.test(value)) document.documentElement.style.setProperty('--'+key,value);
    }
    const data = await api('/api/media');
    items = data.sort((a,b)=>a.position-b.position).map(x=>({...x, thumbnail:`/api/media/${x.id}/thumb`, original:`/api/media/${x.id}/original`, url:`/api/media/${x.id}/thumb`}));
    const ids = new Set(items.map(x=>x.id)); selected = new Set([...selected].filter(id=>ids.has(id))); render();
    document.querySelector('.device').textContent = '● Conectado ao celular · rede local';
  }
  window.poetrateRemote = { api, refresh };
  async function mutation(task) {
    if (busy) return;
    busy = true;
    try { await task(); await refresh(); }
    catch(error) { toast(error.message); }
    finally { busy = false; }
  }
  document.querySelector('.notice').textContent = 'Biblioteca do celular · Miniaturas leves na grade. Ao abrir uma mídia, o original é carregado. Alterações são salvas no celular.';
  document.querySelector('.device').textContent = 'Aguardando conexão com o celular';
  document.querySelector('footer').textContent = 'Poetrate · conexão local, sem nuvem';
  $('import').textContent = '＋ Enviar ao celular'; $('remove').textContent = 'Enviar à lixeira';
  $('position-form').onsubmit = e => {
    e.preventDefault();
    const target = Number($('position').value);
    if (!Number.isInteger(target) || target < 1 || target > items.length) return;
    mutation(async()=>{await api(`/api/media/${moveId}/position`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({position:target})});$('reposition').close();toast('Posição salva no celular.');});
  };
  $('remove').onclick = () => {
    const ids = [...selected];
    if (!ids.length || !confirm(`Enviar ${ids.length} item(ns) à lixeira do celular?`)) return;
    mutation(async()=>{for(const id of ids) await api(`/api/media/${id}/trash`,{method:'POST'});selected.clear();toast('Itens enviados à lixeira do Poetrate.');});
  };
  function upload(file,index,total) {
    return new Promise((resolve,reject)=>{
      const xhr = new XMLHttpRequest();
      xhr.open('POST','/api/upload?name='+encodeURIComponent(file.name));
      xhr.setRequestHeader('Content-Type',file.type || 'application/octet-stream');
      xhr.upload.onprogress = event => { if(event.lengthComputable) document.querySelector('.notice').textContent=`Enviando ${index}/${total}: ${file.name} · ${Math.round(event.loaded/event.total*100)}%`; };
      xhr.onload = ()=>{if(xhr.status>=200 && xhr.status<300) resolve();else {let message='Envio não concluído.';try{message=JSON.parse(xhr.responseText).error||message;}catch{}reject(new Error(message));}};
      xhr.onerror=()=>reject(new Error('Conexão interrompida. Os envios já concluídos permanecem no celular.'));
      xhr.send(file);
    });
  }
  $('files').onchange = () => {
    const files = [...$('files').files]; $('files').value='';
    mutation(async()=>{
      let sent=0;
      try {for(const file of files){await upload(file, sent+1, files.length);sent++;}toast(`${sent} arquivo(s) salvo(s) no celular.`);}
      finally {document.querySelector('.notice').textContent=`${sent}/${files.length} envio(s) concluído(s). Arquivos salvos no álbum Notebook.`;await refresh();}
    });
  };
  const localShow=show;
  show=id=>{localShow(id);const item=items.find(x=>x.id===id);if(item)$('details').textContent=`Posição ${item.position} · ${(item.size/1048576).toFixed(2)} MB · ${new Date(item.date).toLocaleDateString('pt-BR')}`;};
  connection.querySelector('form').onsubmit = async e => {
    e.preventDefault();if(pairing)return;pairing=true;
    const button=connection.querySelector('button');button.disabled=true;
    try {
      const result=await api('/api/pair',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:connection.querySelector('input').value})});
      status.textContent='Confirme “Autorizar” no celular.';
      const deadline=Date.now()+120000;
      while(Date.now()<deadline){await new Promise(resolve=>setTimeout(resolve,1000));const response=await api('/api/pair/status?ticket='+encodeURIComponent(result.ticket));if(response.approved){connection.close();await refresh();return;}}
      throw new Error('Tempo esgotado. Reinicie a conexão no celular.');
    } catch(error){status.textContent=error.message;}
    finally {pairing=false;button.disabled=false;}
  };
  refresh().catch(error=>{status.textContent=error.message;if(!connection.open)connection.showModal();});
  window.addEventListener('focus',()=>{if(!connection.open && !busy)refresh().catch(error=>toast(error.message));});
}
