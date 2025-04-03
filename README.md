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

### Create GIF with transparency
```powershell
$graphFile = get-item football.gml
$magickExe = 'C:\Program Files\ImageMagick-7.1.0-Q16-HDRI\magick.exe'
$outFile = Join-Path $graphFile.Directory ($graphFile.BaseName+'.png')
@(
  @{op='import'; file=$graphFile.FullName }
  @{op='statistics';values=@('Modularity') }
  @{op='colorNodesBy';columnName='modularity_class'}
  @{op='layouts'; values=@(
  @{name='ForceAtlas2'; 'Tolerance (speed)' = 0.1; Scaling=20; steps=40; exportEach=1;
   export=@{op='export';file=$outFile; resolution=@(640,480); timestamp=$true
    PNGExporter=@{transparentBg=$true}
   }
  }
)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander

& $magickExe -delay 0 -loop 0 -dispose previous "$dir\*.png" "$dir\output.gif"
```
<img src="https://github.com/user-attachments/assets/d3fee647-247d-459c-afbf-42648440789a" width="240"/>

