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

### Size nodes by column

Nodes in football.gml have a 'value' numeric property, we can use this column to size nodes. Apart from any numeric nodes properties from the file, some default columns are available: degree, inDegree, outDegree. Optionally you can specify minSize and maxSize.

```powershell
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='sizeNodesBy'; column='value'; minSize=10; maxSize=40}
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; 'LinLog mode'=$true; steps=50}
  )}
  @{op='export';file=$outFile; resolution=@(320,240); }
) | ConvertTo-Json -d 9 | java -jar $gephiCommander
```
![football](https://github.com/user-attachments/assets/c6424a45-e9ca-4e95-b99f-b867f2c6df76)

### Color nodes by community (Modularity)

Applying statistics=Modularity creates a nodes column with id=modularity_class which we can use to color nodes later.

```powershell
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='statistics';values=@('Modularity') }
  @{op='colorNodesBy';columnName='modularity_class'}
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; 'LinLog mode'=$true; steps=200}
  )}
  @{op='export';file=$outFile; }
) | ConvertTo-Json -d 9 | java -jar $gephiCommander
```
![football](https://github.com/user-attachments/assets/860fe61c-9c49-40a1-81e8-089c80d5545c)

### Create GIF
```powershell
$magickExe = 'C:\Program Files\ImageMagick-7.1.0-Q16-HDRI\magick.exe'

$graphFile = Get-ChildItem -Recurse dolphins.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')
@(
  @{op='import'; file=$graphFile.FullName }
  @{op='layouts'; values=@(
  @{name='ForceAtlas2'; 'Tolerance (speed)' = 0.02; Scaling=20; steps=40; exportEach=1;
   export=@{op='export';file=$outFile; resolution=@(320,240); timestamp=$true}
  }
)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander

& $magickExe -delay 0 -loop 0 "$dir\*.png" "$dir\output.gif"
```
![output](https://github.com/user-attachments/assets/3f7601cc-c693-4656-984e-d48b3aaeffb7)

### Create GIF with transparency
```powershell
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

### Follow a node

Put a node id into findNode and reference it in translateX/Y expressions:
```powershell
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')
@(
  @{op='import'; file=$graphFile.FullName }
  @{op='preview'; showNodeLabels=$true}
  @{op='layouts'; values=@(
  @{name='ForceAtlas2'; 'Tolerance (speed)' = 0.01; Scaling=20; steps=40; exportEach=1;
   export=@{op='export';file=$outFile; resolution=@(320,240); timestamp=$true;
    PNGExporter=@{ scaling="1"; translateX="-(nodeX*sc+w/2*(1-sc) -w/2 )/sc"; translateY="-(-nodeY*sc+h/2*(1-sc) -h/2 )/sc"; findNode="23"}
   }
  }
)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander

& $magickExe -delay 0 -loop 0 "$dir\*.png" "$dir\output.gif"
```
![output](https://github.com/user-attachments/assets/77f5884b-eeee-4c6a-bd62-675fa6763d31)
