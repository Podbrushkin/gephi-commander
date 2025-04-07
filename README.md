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
) | ConvertTo-Json -d 9 | java -jar .\target\gephi-commander-0.1.jar -
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

## Download sample GML graphs

Graphs used in examples: dolphins, football, polblogs.

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

## Size nodes by column

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
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
![football](https://github.com/user-attachments/assets/c6424a45-e9ca-4e95-b99f-b867f2c6df76)


## Color nodes by numeric column (Ranking)

football.gml nodes have a **value** property, in graph below nodes with smallest value will be gray, nodes with largest - red. Apart from regular node properties some auto columns can be used: degree, inDegree, outDegree.

```powershell
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='colorNodesBy';column='value'; mode='ranking'; colors=@('gray','red')}
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; 'LinLog mode'=$true; steps=200}
  )}
  @{op='export';file=$outFile; resolution=@(320,240)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
![football](https://github.com/user-attachments/assets/3c91b5bc-5697-46c0-a698-fed3eb7931a8)

Below is an example of how you can change color distribution. Change 0.9 to 0.1 and there will be a lot of red nodes. List of available colors can be found here: [ImportUtils.java](https://github.com/gephi/gephi/blob/master/modules/ImportAPI/src/main/java/org/gephi/io/importer/api/ImportUtils.java)

```powershell
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='colorNodesBy';column='value'; mode='ranking' colors=@('lightblue','lightgray','red');
     colorPositions=@(0, 0.9, 1);
  }
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; 'LinLog mode'=$true; steps=100}
  )}
  @{op='export';file=$outFile; resolution=@(320,240)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
![football](https://github.com/user-attachments/assets/b0ce5009-7ca9-4525-add5-afa08b834d4b)

## Color nodes by String column (Partitioning)

Nodes can be colored in **Partition** mode: all nodes with the same value in provided property will have the same color, colors will be chosen automatically.
I.e. all nodes in polblogs.gml have a **value** property, which is either 0 or 1. Therefore there will be only two colors:

```powershell
$graphFile = get-childitem -recurse polblogs.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='filters';values=@(
    @{name='GiantComponent'}
  )}
  @{op='colorNodesBy'; column='value'; mode='partition' }
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; steps=200 }
  )}
  @{op='export';file=$outFile; resolution=@(320,240)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
![polblogs](https://github.com/user-attachments/assets/b7c9b7c8-7cc4-41dd-8030-1811fef98340)

## Color nodes by property value

If nodes in your file already have a color property:

```powershell
@'
graph
[
  directed 1
  node
  [
    id 0
    label Red
    color red
  ]
  node
  [
    id 1
    label Green
    color #00FF00
  ]
  node
  [
    id 2
    label "Blue"
    color "rgb(0, 0, 255)"
  ]
  node
  [
    id 3
    label "No color property"
  ]
]
'@ > coloredGraphMini.gml
$graphFile = Get-Item coloredGraphMini.gml
$outFile = ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='preview'; showNodeLabels=$true}
  @{op='colorNodesBy'; column='color'; mode='value'; }
  @{op='layouts'; values=@(
    @{name='RandomLayout'; 'Space size'=200; }
  )}
  @{op='export';file=$outFile; resolution=@(320,240)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
start $outFile
```
![colored](https://github.com/user-attachments/assets/1a41870c-13f8-4bdc-9ee9-66a52b9b6d6a)

## Color nodes by community (Modularity)

Applying statistics=Modularity creates a nodes column with id=modularity_class which we can use to color nodes later.

```powershell
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='statistics';values=@('Modularity') }
  @{op='colorNodesBy';column='modularity_class'; mode='partition'}
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; 'LinLog mode'=$true; steps=200}
  )}
  @{op='export';file=$outFile; }
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
![football](https://github.com/user-attachments/assets/860fe61c-9c49-40a1-81e8-089c80d5545c)

## Create GIF
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
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -

& $magickExe -delay 0 -loop 0 "$dir\*.png" "$dir\output.gif"
```
![output](https://github.com/user-attachments/assets/3f7601cc-c693-4656-984e-d48b3aaeffb7)

## Create GIF with transparency
```powershell
$graphFile = get-childitem -recurse football.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')
@(
  @{op='import'; file=$graphFile.FullName }
  @{op='statistics';values=@('Modularity') }
  @{op='colorNodesBy';column='modularity_class'; mode='partition'}
  @{op='layouts'; values=@(
  @{name='ForceAtlas2'; 'Tolerance (speed)' = 0.1; Scaling=20; steps=40; exportEach=1;
   export=@{op='export';file=$outFile; resolution=@(320,240); timestamp=$true
    PNGExporter=@{transparentBg=$true}
   }
  }
)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -

& $magickExe -delay 0 -loop 0 -dispose previous "$dir\*.png" "$dir\output.gif"
```
<img src="https://github.com/user-attachments/assets/d3fee647-247d-459c-afbf-42648440789a" width="240"/>

## Follow a node

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
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -

& $magickExe -delay 0 -loop 0 "$dir\*.png" "$dir\output.gif"
```
![output](https://github.com/user-attachments/assets/77f5884b-eeee-4c6a-bd62-675fa6763d31)

## Live preview

It will display a window where you can examine appearance of your graph. Avoid it when perfomance is needed.

```powershell
@(
  @{op='import'; file=$graphFile.FullName }
  @{op='livePreview'; fps=30}
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; 'Tolerance (speed)' = 0.001; 'LinLog mode'=$true; steps=[int]::MaxValue }
  )}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```