(() => {
  const temperatures = [3.4,2.3,1.5,3.2,2.2,1.9,2.1,1,2.9,6.7,9.3,10.5,10.9,10.2,10.7,11.9,10.9,8.7,7,5.1,4.9,3.2,2.8];
  const section = document.createElement('section');
  section.innerHTML = '<div class="heading"><div><p class="eyebrow">HOURLY TEMPERATURE</p><h2>11 月 19 日天气实况</h2></div><p>阜阳 · 截至 22:00</p></div><p>整点气温最低 1°C，最高 11.9°C；22 时气温 2.8°C。夜间晴为天气预报。</p><div style="overflow-x:auto"><svg id="weather-chart" viewBox="0 0 800 260" role="img" aria-label="11月19日0时至22时整点气温变化曲线" style="width:100%;min-width:550px;height:auto"></svg></div><p class="note">根据<a href="https://www.weather.com.cn/weather1d/101220801.shtml">中国天气网阜阳整点实况</a>数据绘制，采集于 22 时后；23 时数据尚未纳入。气温为整点观测值，并非全天极值。</p>';
  document.querySelector('.overview').after(section);
  const svg=section.querySelector('svg'), ns='http://www.w3.org/2000/svg';
  function add(name, attrs, text){const node=document.createElementNS(ns,name);for(const [k,v] of Object.entries(attrs))node.setAttribute(k,v);if(text!==undefined)node.textContent=text;svg.appendChild(node)}
  for(let t=0;t<=12;t+=3){let y=205-t*14;add('line',{x1:40,y1:y,x2:770,y2:y,stroke:'#dce5dd'});add('text',{x:8,y:y+4,fill:'#64776a','font-size':12},t+'°')}
  const pts=temperatures.map((v,i)=>[45+i*32.5,205-v*14]);
  add('polyline',{points:pts.map(p=>p.join(',')).join(' '),fill:'none',stroke:'#e4874d','stroke-width':3});
  pts.forEach(([x,y],i)=>{add('circle',{cx:x,cy:y,r:3,fill:'#e4874d'});if(i%3===0||i===22){add('text',{x,y:232,'text-anchor':'middle',fill:'#64776a','font-size':12},String(i).padStart(2,'0')+':00')}});
})();
