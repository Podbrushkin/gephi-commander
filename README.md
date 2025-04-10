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

Graphs used in examples: dolphins, football, polblogs, lesmis.

Examples below will work as-is if these graphs are somewhere in current directory.

```powershell
$url = 'https://websites.umich.edu/~mejn/netdata/'
$html = Invoke-RestMethod $url
$html | Select-String '([^"]*?\.zip)\"' -allmatches | % Matches | % {$_.groups[1].value} | Out-GridView -PassThru | % {
  Invoke-RestMethod ($url+$_) -OutFile $_
  Start-Sleep -mill 500
}

Get-ChildItem *.zip | % {Expand-Archive $_ -Force}

@'
graph
[
  directed 1
  node
  [
    id 0
    label "Red 0,0"
    color red
    x 0
    y 0
  ]
  node
  [
    id 1
    label "Green 100,200"
    color #00FF00
    x 100
    y 200
  ]
  node
  [
    id 2
    label "Blue -100,100"
    color "rgb(0, 0, 255)"
    x -100
    y 100
  ]
  node
  [
    id 3
    label "No color property -100,-200"
    x -100
    y -200
  ]
  edge
  [
    source 0
    target 1
    group a
    color Pink
  ]
  edge
  [
    source 1
    target 2
    group b
    color "rgb(125, 60, 152)"
  ]
  edge
  [
    source 2
    target 3
    group b
    color #a04000
  ]
  edge
  [
    source 1
    target 3
    group c
  ]
]
'@ > sampleGraphMini.gml
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


## Color nodes (Ranking mode)

football.gml nodes have a numeric property **value**, in graph below nodes with smallest value will be gray, nodes with largest - red. Apart from regular node properties these auto columns can be used: degree, inDegree, outDegree.

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
  @{op='colorNodesBy';column='value'; mode='ranking'; colors=@('lightblue','lightgray','red');
     colorPositions=@(0, 0.9, 1);
  }
  @{op='layouts'; values=@(
    @{name='ForceAtlas2'; 'LinLog mode'=$true; steps=100}
  )}
  @{op='export';file=$outFile; resolution=@(320,240)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
![football](https://github.com/user-attachments/assets/b0ce5009-7ca9-4525-add5-afa08b834d4b)

## Color nodes (Partition mode)

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

## Color nodes (Value mode)

If nodes in your file already have a color property:

```powershell
$graphFile = Get-Item sampleGraphMini.gml
$outFile = ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='preview'; nodeLabelShow=$true}
  @{op='colorNodesBy'; column='color'; mode='value'; }
  @{op='export';file=$outFile; resolution=@(320,240)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
![coloredGraphMini](https://github.com/user-attachments/assets/ca81ac84-1ec5-43f2-9145-517b514b6edc)

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

## Color edges (Partition mode)

Edges with the same value in specified column will have the same color.
I.e. edges in **sampleGraphMini.gml** have **group** property with value of a, b or c, so they will be colored to 3 random colors total:

```powershell
$graphFile = Get-Item .\sampleGraphMini.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='colorEdgesBy';column='group'; mode='partition'}
  @{op='preview'; 'background-color'='DimGray'; }
  @{op='export';file=$outFile; resolution=@(640,480)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
<img src="https://github.com/user-attachments/assets/88512aa0-69bf-4d54-8843-e56f8160ece4" width="240"/>

## Color edges (Ranking mode)

If your edges have numeric property, you can apply color to them based on this property. 

Edges in **lesmis.gml** have a `value` property, which internally intrepreted as weight and renamed to `weight`, below coloring is applied based on this column with default colors - blue,yellow,red:

```powershell
$graphFile = Get-ChildItem -recurse lesmis.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='colorEdgesBy';column='weight'; mode='ranking'}
  @{op='preview'; 'background-color'='DimGray'; }
  @{op='layouts'; values=@(
    @{name='FruchtermanReingold'; steps=200; }
  )}
  @{op='export';file=$outFile; resolution=@(640,480)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
<img src="https://github.com/user-attachments/assets/38e77de6-6aaf-4e7f-88a3-7280e62f0857" width="240"/>

You can specify your own colors and color positions:
```powershell
$graphFile = Get-ChildItem -recurse lesmis.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='colorEdgesBy';column='weight'; mode='ranking'; colors=@('gray','red'); colorPositions=@(0.1,1)}
  @{op='preview'; 'background-color'='DimGray'; }
  @{op='layouts'; values=@(
    @{name='FruchtermanReingold'; steps=200; }
  )}
  @{op='export';file=$outFile; resolution=@(640,480)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
<img src="https://github.com/user-attachments/assets/193f6bb8-dee2-4797-b276-7e2c76aebb62" width="240"/>

## Color edges (Value mode)

If your edges already have a color specified in one of their properties (as in **sampleGraphMini.gml**), use mode=value:

```powershell
$graphFile = Get-Item .\sampleGraphMini.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='colorEdgesBy';column='color'; mode='value'}
  @{op='preview'; 'background-color'='DimGray'; edgeThickness=5; }
  @{op='export';file=$outFile; resolution=@(640,480)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -
```
<img src="https://github.com/user-attachments/assets/4f934bfc-37ad-4d9f-b007-b1aa1d142936" width="240"/>

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

$outFile = "$dir\output.gif"
& $magickExe -delay 0 -loop 0 "$dir\*.png" $outFile
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

$outFile = "$dir\output.gif"
& $magickExe -delay 0 -loop 0 -dispose previous "$dir\*.png" $outFile
```
<img src="https://github.com/user-attachments/assets/d3fee647-247d-459c-afbf-42648440789a" width="240"/>

## Follow a node

Put a node id into findNode and reference it in centerOnX/Y expressions:
```powershell
$graphFile = get-childitem -recurse dolphins.gml
$dir = $graphFile.Directory
$outFile = Join-Path $dir ($graphFile.BaseName+'.png')

@(
  @{op='import'; file=$graphFile.FullName }
  @{op='preview'; 'background-color'='DimGray'; 'nodeLabelShow'=$true; nodeLabelColor='White' }
  @{op='layouts'; values=@(
   @{name='ForceAtlas2'; 'Tolerance (speed)' = 0.01; Scaling=20; steps=40; exportEach=1;
      export=@{op='export';file=$outFile; resolution=@(320,240); timestamp=$true
       PNGExporter=@{scaling=0.6; centerOnX='nodeX'; centerOnY='nodeY'; findNode="2" }
      }
   }
)}
) | ConvertTo-Json -d 9 | java -jar $gephiCommander -

& $magickExe -delay 0 -loop 0 -dispose previous "$dir\*.png" "$dir\output.gif"
gci $dir *.png | Remove-Item
start "$dir\output.gif"
```
![output](https://github.com/user-attachments/assets/97eea1c9-5fd5-4e62-91db-60d5094dac17)

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