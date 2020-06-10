
const createAccordion = (json) => {
    const groupedByMaven = groupBy(json, 'mavenVersion');
    return Object.entries(groupedByMaven).map(([key, value]) => `<li>
    <div class="collapsible-header canvasDiv"><i class="material-icons">filter_drama</i>${key}
    <div class="svgDisplay">
          <canvas id="${key}linechart"></canvas>        
          <canvas id="${key}piechart"></canvas>        
        </div>
    </div>
    <div class="collapsible-body">  
    ${value.map(val => `<details><summary><span class="commit">Commit id: ${val.gitSha}</span> | <span class="message">
    ${val.commitMessage.length > 50 ? val.commitMessage.slice(0, 50) + '...' : val.commitMessage} 
    </span>| <span class="author">${val.author} </span>| <span class="date">${new Date(val.timestamp * 1000).toLocaleString()} </span></summary>
    <div class="row">
    <div class="col s12">
      <div class="card light-blue darken-1">
        <div class="card-content white-text">
          <span class="card-title">Commit created on <span>${new Date(val.timestamp * 1000).toLocaleString()}</span></span>
         <div class="row">
         <div class="col s6"><span class="title">Author:</span> ${val.author}</div>
         <div class="col s6"><span class="title">Commit Message:</span> ${val.commitMessage}</div>         
         <div class="col s6"><span class="title">Entries</span> 
         <ul class="collection">
         ${val.entries.map(entries => `<li class="collection-item avatar">
         <i class="material-icons circle">folder</i>
         <span class="title listTitle">${entries}</span>         
         </li>`).join('')}
         </ul>
         </div>                  
         <div class="col s6"><span class="title">Tickets</span> 
         <ul class="collection">
         ${val.tickets.map(ticket => `<li class="collection-item avatar">
         <i class="material-icons circle">folder</i>
         <span class="title listTitle">${ticket}</span>         
         </li>`).join('')}
         </ul>
         </div>                  
         </div>
        </div>       
      </div>
    </div>
  </div>
    </details>`).join('')}
  </div>
  </li>`).join('')
}

const groupBy = (arr, key) => {
    return arr.reduce((iterator, value) => {
        (iterator[value[key]] = iterator[value[key]] || []).push(value);
        return iterator;
    }, {})
}

const main = () => {
    document.addEventListener('DOMContentLoaded', function () {
        const options = {
            accordion: false
        }
        const elems = document.querySelectorAll('.collapsible');
        const instances = M.Collapsible.init(elems, options);

    });
    const jsonVal = JSON.parse(document.getElementById('dataJson').firstChild.data);
    createView(jsonVal)
}

const createView = (jsonVal) => {
    document.getElementById('dynamicAccordion').innerHTML = createAccordion(jsonVal);
    createCommitCharts(jsonVal);
}

const search = () => {
    const value = document.getElementById('search').value;
    const jsonVal = JSON.parse(document.getElementById('dataJson').firstChild.data);
    console.log(value);
    if (value.length > 0) {
        const newJson = filterByValue(jsonVal, value.trim());
        createView(newJson);
        document.querySelectorAll('.collapsible-header').forEach(val => val.classList.add("active"));
        const elems = document.querySelectorAll('.collapsible');
        let highlight = value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        let re = new RegExp(highlight, 'g');
        elems.forEach(elem => {
            elem.innerHTML = elem.innerHTML.replace(re, `<mark>$&</mark>`);
            let instance = M.Collapsible.getInstance(elem);
            instance.open();
        })
    } else {
        createView(jsonVal);
    }
}

const filterByValue = (array, string) => {
    return array.filter(o => Object.keys(o).some(k => o[k].toString().toLowerCase().includes(string.toLowerCase())));
}

const createCommitCharts = (jsonVal) => {
    const groupedByMaven = groupBy(jsonVal, 'mavenVersion');
    Object.entries(groupedByMaven).forEach(([keyString, value]) => {
        const numberOfCommits = groupBy(value, 'day');
        {
            let data = [];
            let labels = [];
            Object.entries(numberOfCommits).map(([key, value]) => {
                labels.push(key);
                data.push(value.length);
            })
            let borderColor = Array(data.length).fill('rgba(255, 152, 0,1)');
            const ctx = document.getElementById(keyString + 'linechart').getContext('2d');
            const myChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        data: data,
                        borderColor: borderColor,
                        backgroundColor: 'rgba(0, 0, 0, 0)',
                        borderWidth: 1,
                        pointRadius: 1
                    }]
                },
                options: {
                    legend: {
                        display: false,
                    },
                    tooltips: {
                        position: 'nearest',
                        mode: 'index',
                        intersect: false,
                        callbacks: {
                            beforeTitle: function (tooltipItem, data) {
                                const title = `${tooltipItem[0].label} : ${tooltipItem[0].value}`;

                                /*   if (title) {
                                      title += ': ';
                                  }
                                  title += Math.round(tooltipItem.yLabel * 100) / 100; */
                                return title;
                            },
                            label: function () {
                                return '';
                            },
                            title: function () {
                                return '';
                            }
                        }
                    },
                    scales: {
                        yAxes: [
                            {
                                display: false,
                                ticks: {
                                    beginAtZero: true
                                }
                            }],
                        xAxes: [
                            {
                                display: false
                            }
                        ]
                    }
                }
            });
        }
        {
            const activeContributors = groupBy(value, 'author');
            let data = [];
            let labels = [];
            let totalLength = Object.keys(activeContributors).length;
            Object.entries(activeContributors).map(([key, value]) => {
                labels.push(key.slice(0, 7) + '...');
                let percentage = (value.length / totalLength)
                data.push(percentage.toFixed(2));
            })
            let borderColor = Array(data.length).fill('rgba(255, 152, 0,1)');
            const ctx2 = document.getElementById(keyString + 'piechart').getContext('2d');
            const myChart2 = new Chart(ctx2, {
                type: 'pie',
                data: {
                    labels: labels,
                    datasets: [{
                        data: data,
                        backgroundColor: ['red','blue', 'green', 'pink', 'brown', 'yellow', 'purple', 'aqua', 'teal', 'orange']
                    }]
                },
                options: {
                    legend: {
                        display: false,
                    },
                }
            });
        }

    })
}

main()
// Event Handlers for Page Action
document.getElementById('search').onkeyup = search;
