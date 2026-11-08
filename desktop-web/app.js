'use strict';
const $ = id => document.getElementById(id);
const palettes = [['#83a6aa','#385e65','#203e46'],['#b8a68b','#7d7764','#4c5948'],['#bdb6a3','#788e7a','#405749'],['#cba289','#9c7368','#584e53'],['#889caf','#5a718b','#35485d'],['#b6b68b','#6e8966','#365c50']];
function landscape(i) {
  const [sky, back, front] = palettes[i % palettes.length];
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="900" viewBox="0 0 1200 900"><rect width="1200" height="900" fill="${sky}"/><circle cx="${220+i*53%750}" cy="220" r="70" fill="#f0e4c9" opacity=".7"/><path d="M0 650L290 ${280+i*23%200}L620 610L870 340L1200 590V900H0Z" fill="${back}"/><path d="M0 730Q300 410 600 690T1200 630V900H0Z" fill="${front}"/><path d="M350 900Q760 720 600 640Q920 780 790 900" fill="${sky}" opacity=".4"/></svg>`;
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}
const names = ['Primeira luz','Entre montanhas','Caminhos de casa','Silêncio verde','Depois da chuva','Horizonte','Um dia tranquilo','Luz de fim de tarde','Ao longe','Pequenas pausas','Perto do rio','Memórias de viagem'];
let items = names.map((name,i)=>({id:`demo-${i}`,name:name+'.svg',type:'image',url:landscape(i),demo:true,size:null}));
if (location.protocol !== 'file:') items = [];
let selected = new Set(), filter = 'all', currentId = null, moveId = null;
const objectUrls = [];
function visible(){ const query=$('search').value.toLocaleLowerCase();return items.slice().reverse().filter(x=>(filter==='all'||x.type===filter)&&x.name.toLocaleLowerCase().includes(query)); }
function toast(message){$('toast').textContent=message;$('toast').style.display='block';clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').style.display='none',3500);}
function render(){
  const grid=$('grid');grid.replaceChildren();const list=visible();$('count').textContent=`${list.length} de ${items.length} itens`;$('empty').hidden=!!list.length;
  list.forEach(item=>{
    const card=document.createElement('article');card.className='card'+(selected.has(item.id)?' selected':'');
    const open=document.createElement('button');open.className='open';open.setAttribute('aria-label','Abrir '+item.name);
    const image=document.createElement('img');image.alt=item.demo?'Paisagem ilustrada de demonstração':item.name;image.loading='lazy';image.src=item.thumbnail || (item.type==='video'?landscape(0):item.url);open.append(image);open.onclick=()=>show(item.id);
    const check=document.createElement('input');check.type='checkbox';check.className='check';check.checked=selected.has(item.id);check.setAttribute('aria-label','Selecionar '+item.name);check.onchange=()=>{check.checked?selected.add(item.id):selected.delete(item.id);render();};
    const caption=document.createElement('div');caption.className='caption';const name=document.createElement('span');name.textContent=item.name;const position=document.createElement('small');position.textContent='#'+(items.indexOf(item)+1);caption.append(name,position);card.append(open,check,caption);
    if(item.type==='video'){const tag=document.createElement('span');tag.className='video-tag';tag.textContent=item.thumbnail?'▷ Vídeo':'▷ Vídeo · prévia genérica';card.append(tag);}
    grid.append(card);
  });
  $('selection').hidden=!selected.size;$('selected-count').textContent=`${selected.size} selecionado(s)`;$('move').disabled=selected.size!==1;
}
function zoom(){const img=$('stage').querySelector('img');const value=Number($('zoom').value);$('zoom-value').value=value+'%';if(img){img.style.width=(Math.min($('stage').clientWidth,1200)*value/100)+'px';img.style.height='auto';}}
function show(id){
  const item=items.find(x=>x.id===id);if(!item)return;currentId=id;$('stage').replaceChildren();$('title').textContent=item.name;
  const media=document.createElement(item.type==='video'?'video':'img');media.src=item.original || item.url;
  if(item.type==='video'){media.controls=true;media.preload='metadata';}else media.alt=item.name;
  $('stage').append(media);$('details').textContent=`Posição ${items.indexOf(item)+1} de ${items.length} · ${item.demo?'Ilustração de demonstração':(item.size/1048576).toFixed(2)+' MB · arquivo local desta sessão'}`;
  $('zoom-label').hidden=item.type==='video';$('zoom').value=100;if(!$('viewer').open)$('viewer').showModal();zoom();
  const list=visible(), index=list.findIndex(x=>x.id===id);$('previous').disabled=index<=0;$('next').disabled=index<0||index>=list.length-1;
}
function navigate(delta){const list=visible(),index=list.findIndex(x=>x.id===currentId);if(list[index+delta])show(list[index+delta].id);}
$('previous').onclick=()=>navigate(-1);$('next').onclick=()=>navigate(1);$('close').onclick=()=>$('viewer').close();$('viewer').addEventListener('close',()=>{$('stage').replaceChildren();currentId=null;});$('zoom').oninput=zoom;
$('viewer').addEventListener('keydown',e=>{if(e.target.matches('input,video'))return;if(e.key==='ArrowRight')navigate(1);if(e.key==='ArrowLeft')navigate(-1);});
$('search').oninput=render;$('size').oninput=()=>document.documentElement.style.setProperty('--tile',$('size').value+'px');
document.querySelectorAll('[data-filter]').forEach(button=>button.onclick=()=>{filter=button.dataset.filter;document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('active',x===button));render();});
$('clear').onclick=()=>{selected.clear();render();};
$('move').onclick=()=>{moveId=[...selected][0];const index=items.findIndex(x=>x.id===moveId);if(index<0)return;$('position').max=items.length;$('position').value=index+1;$('position-help').textContent=`Posição atual: ${index+1}. Escolha de 1 a ${items.length}.`;$('reposition').showModal();$('position').select();};
$('cancel-move').onclick=()=>$('reposition').close();
$('position-form').onsubmit=e=>{e.preventDefault();const target=Number($('position').value),from=items.findIndex(x=>x.id===moveId);if(!Number.isInteger(target)||target<1||target>items.length||from<0)return;items.splice(target-1,0,items.splice(from,1)[0]);$('reposition').close();render();toast(`Item movido para a posição ${target}. Apenas nesta demonstração.`);};
$('remove').onclick=()=>{items=items.filter(x=>!selected.has(x.id));selected.clear();render();toast('Itens removidos da demonstração. Os arquivos originais não foram apagados.');};
$('import').onclick=()=>$('files').click();$('files').onchange=()=>{let count=0;for(const file of $('files').files){if(!/^(image|video)\//.test(file.type))continue;const url=URL.createObjectURL(file);objectUrls.push(url);items.push({id:globalThis.crypto?.randomUUID?.() || String(Date.now()+Math.random()),name:file.name,type:file.type.startsWith('video/')?'video':'image',url,size:file.size});count++;}render();toast(`${count} arquivo(s) aberto(s) localmente, sem envio.`);$('files').value='';};
window.addEventListener('beforeunload',()=>objectUrls.forEach(url=>URL.revokeObjectURL(url)));render();
