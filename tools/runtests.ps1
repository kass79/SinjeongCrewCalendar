# ASCII-only. Runs the app's JVM unit tests with JUnitCore (gradle test breaks on the Korean path).
# Usage (PowerShell 5.1):  powershell -ExecutionPolicy Bypass -File tools\runtests.ps1
# Compile first:           .\gradlew.bat :app:compileDebugUnitTestKotlin
$app  = 'C:\Users\admin\Downloads\07_' + [char]0xD504 + [char]0xB85C + [char]0xC81D + [char]0xD2B8 + '\SinjeongCrewCalendar\app'
$test = "$app\build\tmp\kotlin-classes\debugUnitTest"
$main = "$app\build\tmp\kotlin-classes\debug"
$g    = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$junit = Get-ChildItem $g -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1 -ExpandProperty FullName
$ham   = Get-ChildItem $g -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1 -ExpandProperty FullName
$std   = Get-ChildItem $g -Recurse -Filter 'kotlin-stdlib-*.jar' | Where-Object { $_.Name -notmatch 'sources|common|jdk' } | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
$cp = "$test;$main;$junit;$ham;$std"
$classes = Get-ChildItem $test -Recurse -Filter '*Test.class' | ForEach-Object {
  $_.FullName.Substring($test.Length + 1) -replace '\\', '.' -replace '\.class$', ''
}
Write-Output ("classes: " + ($classes -join ' '))
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
  $jb = "$env:ProgramFiles\Android\Android Studio\jbr\bin\java.exe"
  if (Test-Path $jb) { $java = $jb } else { throw 'java not found' }
} else { $java = $java.Source }
& $java -cp $cp org.junit.runner.JUnitCore @classes
