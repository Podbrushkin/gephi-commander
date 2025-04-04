## Quick Start
```powershell
# git clone & cd
mvn clean compile package -q -e

$graphFile = Get-Item C:\Data\gmlGraphs\dolphins\dolphins.gml
$outFile = 'dolphins.png'

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; steps=200}
  )}
  @{op='export';file=$outFile}
) | ConvertTo-Json -d 9 | java -jar .\target\gephi-commander-0.1.jar
```
```json
[
  {
    "op": "import",
    "file": "C:\\Data\\gmlGraphs\\dolphins\\dolphins.gml"
  },
  {
    "op": "layouts",
    "values": [
      {
        "name": "ForceAtlas2",
        "steps": 200
      }
    ]
  },
  {
    "op": "export",
    "file": "dolphins.png"
  }
]
```

### Download sample GML graphs

Graphs used in examples: dolphins and football.

Examples below will work as-is if these graphs are somewhere in current directory.

```powershell
$url = 'https://websites.umich.edu/~mejn/netdata/'
$html = Invoke-RestMethod $url
$html | Select-String '([^"]*?\.zip)\"' -allmatches | % Matches | % {$_.groups[1].value} | Out-GridView -PassThru | % {
  Invoke-RestMethod ($url+$_) -OutFile $_
  Start-Sleep -mill 500
}

Get-ChildItem *.zip | % {Expand-Archive $_ -Force}
```

### Create GIF with transparency
```powershell
$magickExe = 'C:\Program Files\ImageMagick-7.1.0-Q16-HDRI\magick.exe'
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')
@(
  @{op='import'; file=$graphFile.FullName }
  @{op='statistics';values=@('Modularity') }
  @{op='colorNodesBy';columnName='modularity_class'}
  @{op='layouts'; values=@(
  @{name='ForceAtlas2'; 'Tolerance (speed)' = 0.1; Scaling=20; steps=40; exportEach=1;
   export=@{op='export';file=$outFile; resolution=@(320,240); timestamp=$true
    PNGExporter=@{transparentBg=$true}
   }
  }
)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander

& $magickExe -delay 0 -loop 0 -dispose previous "$dir\*.png" "$dir\output.gif"
```
<img src="https://github.com/user-attachments/assets/d3fee647-247d-459c-afbf-42648440789a" width="240"/>

