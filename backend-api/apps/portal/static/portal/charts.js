/* Gráficos del portal (Chart.js 4, incluido localmente). Paleta coherente con la app. */
const PT = ['#00796b', '#e76f51', '#1d4e89', '#e0a100', '#6a4c93', '#2a9d8f', '#9b2c20', '#4a5e5c'];
Chart.defaults.font.family = 'system-ui, -apple-system, Segoe UI, Roboto, sans-serif';
Chart.defaults.color = '#4a5e5c';
Chart.defaults.maintainAspectRatio = false;
function ptEmpty(id, d) {
  if (d.values.reduce((a, b) => a + b, 0) > 0) return false;
  const box = document.getElementById(id).parentElement;
  box.innerHTML = '<div style="height:100%;display:grid;place-items:center;color:#4a5e5c">Sin datos aún</div>';
  return true;
}
function ptLine(id, d, label) {
  new Chart(document.getElementById(id), {type: 'line', data: {labels: d.labels, datasets: [{label, data: d.values,
    borderColor: PT[0], backgroundColor: 'rgba(0,121,107,.12)', fill: true, tension: .3, pointRadius: 3}]},
    options: {plugins: {legend: {display: false}}, scales: {y: {beginAtZero: true, ticks: {precision: 0}}}}});
}
function ptBar(id, d, label, horizontal) {
  if (ptEmpty(id, d)) return;
  new Chart(document.getElementById(id), {type: 'bar', data: {labels: d.labels, datasets: [{label, data: d.values,
    backgroundColor: PT[0], borderRadius: 6}]},
    options: {indexAxis: horizontal ? 'y' : 'x', plugins: {legend: {display: false}},
      scales: {x: {beginAtZero: true, ticks: {precision: 0, callback: horizontal ? undefined : ptWrapTick}},
               y: {ticks: {precision: 0, callback: horizontal ? ptWrapTick : undefined}}}}});
}
// Divide etiquetas largas (p. ej. nombres de veterinarias) en varias líneas para que no se recorten.
function ptWrapTick(value) {
  const label = String(this.getLabelForValue(value)), lines = [];
  let line = '';
  for (const word of label.split(' ')) {
    if ((line + ' ' + word).trim().length > 18 && line) { lines.push(line); line = word; }
    else line = (line + ' ' + word).trim();
  }
  if (line) lines.push(line);
  return lines;
}
function ptDoughnut(id, d, colors) {
  if (ptEmpty(id, d)) return;
  new Chart(document.getElementById(id), {type: 'doughnut', data: {labels: d.labels, datasets: [{data: d.values,
    backgroundColor: colors || PT, borderWidth: 2}]}, options: {plugins: {legend: {position: 'right'}}, cutout: '58%'}});
}
