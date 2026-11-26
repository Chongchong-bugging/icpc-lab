param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Goals
)

# 本地 Maven 仓库固定在 workspace 内：默认 ~/.m2 在沙箱外，写入会被拒绝。
$repo = Join-Path $PSScriptRoot "..\.cache\m2repo"
$env:MAVEN_ARGS = "-Dmaven.repo.local=$($repo -replace '\\', '/')"

$pom = Join-Path $PSScriptRoot "..\apps\api\pom.xml"
& mvn -f $pom @Goals
exit $LASTEXITCODE