param(
    [Parameter(Mandatory = $true)]
    [string] $ServerBundle,

    [Parameter(Mandatory = $true)]
    [string] $ServerMappings,

    [Parameter(Mandatory = $true)]
    [string] $JavaHome
)

$ErrorActionPreference = 'Stop'
$ExpectedBundleSha256 = '08ABF384C48AFB9E822144AD8A99166482857994389269E26C6A04C6C91D9171'
$ExpectedMappingsSha256 = 'C6FE95810B05DFEC19FDBDFB8CBBB8F976923F7CB16FCC828A0B605A49D2C492'

function Assert-Sha256([string] $Path, [string] $Expected) {
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual -ne $Expected) {
        throw "SHA-256 mismatch for $Path. Expected $Expected, got $actual"
    }
}

$bundle = (Resolve-Path -LiteralPath $ServerBundle).Path
$mappings = (Resolve-Path -LiteralPath $ServerMappings).Path
$jar = Join-Path $JavaHome 'bin\jar.exe'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
Assert-Sha256 $bundle $ExpectedBundleSha256
Assert-Sha256 $mappings $ExpectedMappingsSha256

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) "velocidialog-direct-codec-$PID"
$classes = Join-Path $temporaryRoot 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null

try {
    Push-Location $temporaryRoot
    try {
        & $jar xf $bundle 'META-INF/libraries' 'META-INF/versions/1.21.6/server-1.21.6.jar'
        if ($LASTEXITCODE -ne 0) {
            throw "jar extraction failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }

    $server = Join-Path $temporaryRoot 'META-INF\versions\1.21.6\server-1.21.6.jar'
    $libraries = Get-ChildItem (Join-Path $temporaryRoot 'META-INF\libraries') -Recurse -Filter '*.jar'
    $compileClasspath = (@($server) + @($libraries.FullName)) -join [IO.Path]::PathSeparator
    $probe = Join-Path $PSScriptRoot 'probe\OfficialDialogCodecProbe.java'
    & $javac -proc:none -d $classes -cp $compileClasspath $probe
    if ($LASTEXITCODE -ne 0) {
        throw "probe compilation failed with exit code $LASTEXITCODE"
    }

    $runtimeClasspath = (@($classes, $server) + @($libraries.FullName)) -join [IO.Path]::PathSeparator
    $fixtures = Join-Path $PSScriptRoot '..\..\src\test\resources\fixtures\mojang-dialog-direct-codec-1.21.6'
    $inputs = @(Get-ChildItem $fixtures -Filter '*.json' | Sort-Object Name)
    $probeArguments = [Collections.Generic.List[string]]::new()
    foreach ($input in $inputs) {
        $json = [IO.File]::ReadAllText($input.FullName, [Text.Encoding]::UTF8)
        $probeArguments.Add($input.BaseName)
        $probeArguments.Add([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json)))
    }
    # Mojang's bootstrap configures Log4j and creates logs/ relative to the process directory.
    # Keep those generated files inside the temporary root so fixture regeneration never dirties
    # the repository.
    Push-Location $temporaryRoot
    try {
        $output = & $java -cp $runtimeClasspath probe.OfficialDialogCodecProbe $probeArguments 2>&1
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) {
        throw "DIRECT_CODEC failed: $($output -join [Environment]::NewLine)"
    }

    foreach ($input in $inputs) {
        $marker = "DIRECT_CODEC_FIXTURE $($input.BaseName) "
        $line = $output | Where-Object { $_.ToString().Contains($marker) } | Select-Object -Last 1
        $match = [regex]::Match($line.ToString(), [regex]::Escape($marker) + '(.+)$')
        if (-not $match.Success) {
            throw "Could not find encoded NBT for $($input.Name): $($output -join [Environment]::NewLine)"
        }
        $destination = [IO.Path]::ChangeExtension($input.FullName, '.snbt')
        [IO.File]::WriteAllText(
            $destination,
            $match.Groups[1].Value + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false)
        )
        Write-Host "Generated $destination"
    }
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
