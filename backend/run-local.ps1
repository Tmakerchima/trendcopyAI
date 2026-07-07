$ErrorActionPreference = "Stop"

$proxy = Get-ItemProperty "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
$javaArgs = @()

if ($proxy.ProxyEnable -eq 1 -and $proxy.ProxyServer) {
  $proxyAddress = ($proxy.ProxyServer -split ";")[0]
  if ($proxyAddress -match "^(?:http=)?(?<host>[^:]+):(?<port>\d+)$") {
    $hostName = $Matches.host
    $port = $Matches.port
    $javaArgs += "-Dhttp.proxyHost=$hostName"
    $javaArgs += "-Dhttp.proxyPort=$port"
    $javaArgs += "-Dhttps.proxyHost=$hostName"
    $javaArgs += "-Dhttps.proxyPort=$port"
    Write-Host "Using local proxy $hostName`:$port for Java outbound requests."
  }
}

$javaArgs += "-jar"
$javaArgs += "target\gamecopy-backend-0.2.0.jar"
$javaArgs += "--spring.profiles.active=local"

java @javaArgs
