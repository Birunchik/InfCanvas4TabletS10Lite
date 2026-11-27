#requires -Version 5.1
<#
.SYNOPSIS
  Short Gradle problem summary instead of the full log.

.DESCRIPTION
  Runs Gradle with --console=plain, stores the full output in build/logs/,
  and prints only compilation errors (e:), warnings (w:), failed tasks,
  test failures, lint summary and the "What went wrong" block.
  The full log is never printed - read it from the reported path if needed.

  Exit code equals Gradle's exit code (0 = success).

.PARAMETER Task
  Gradle tasks; pass them as separate arguments, e.g.
  `./scripts/gradle-issues.ps1 lintDebug buildHealth`.
  In PowerShell 5.1 `powershell -File` can pass only one string argument, so
  multi-word commands need `-Command` or PowerShell 7.

.PARAMETER MaxLines
  Per-section line cap (default 25).

.EXAMPLE
  ./scripts/gradle-issues.ps1 testDebugUnitTest

.EXAMPLE
  ./scripts/gradle-issues.ps1 lintDebug buildHealth -MaxLines 10
#>
param(
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true, Position = 0)]
    [string[]] $Task,
    [int] $MaxLines = 25
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root 'build/logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logPath = Join-Path $logDir "gradle-$stamp.log"

$gradlew = Join-Path $root 'gradlew.bat'
if (-not (Test-Path $gradlew)) { $gradlew = Join-Path $root 'gradlew' }

Push-Location $root
try {
    # PowerShell 5.1 turns native stderr into non-terminating errors; Gradle (and its
    # "WARNING:" lines) writes to stderr, so keep it flowing into the log instead.
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $gradlew @Task --console=plain --warning-mode=none *> $logPath
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
} finally {
    Pop-Location
}

$lines = @(Get-Content -Path $logPath -ErrorAction SilentlyContinue)

function Write-Section {
    param([string] $Title, [string[]] $Items, [int] $Limit)
    if ($Items.Count -eq 0) { return }
    Write-Host ""
    Write-Host ("== {0} ({1}) ==" -f $Title, $Items.Count) -ForegroundColor Yellow
    $Items | Select-Object -First $Limit | ForEach-Object { Write-Host ("  " + $_.Trim()) }
    if ($Items.Count -gt $Limit) {
        $hidden = $Items.Count - $Limit
        Write-Host ("  ... {0} more lines; full log: {1}" -f $hidden, $logPath) -ForegroundColor DarkGray
    }
}

$kotlinErrors = @($lines | Where-Object { $_ -match '^\s*e: ' })
$kotlinWarnings = @($lines | Where-Object { $_ -match '^\s*w: ' })
$failedTasks = @($lines | Where-Object { $_ -match '^> Task .* FAILED' })
$testFailures = @($lines | Where-Object { $_ -match '\d+ tests completed, \d+ failed' })
$lintIssues = @($lines | Where-Object { $_ -match 'Lint found|Errors? found|Warnings? found' })
$problems = @($lines | Where-Object { $_ -match '^\* What went wrong:' })
$causes = @($lines | Where-Object { $_ -match '^(Caused by:|\s+at )' } | Select-Object -First 12)
$result = @($lines | Where-Object { $_ -match '^BUILD (FAILED|SUCCESSFUL)' })

Write-Section 'Compilation errors (e:)' $kotlinErrors $MaxLines
Write-Section 'Compilation warnings (w:)' $kotlinWarnings ([Math]::Min($MaxLines, 10))
Write-Section 'Failed tasks' $failedTasks $MaxLines
Write-Section 'Test failures' $testFailures $MaxLines
Write-Section 'Lint summary' $lintIssues $MaxLines

if ($problems.Count -gt 0) {
    $start = $lines.IndexOf($problems[0])
    $block = @($lines[$start..([Math]::Min($start + 6, $lines.Count - 1))] | Where-Object { $_ -and $_ -notmatch '^\* Try:' })
    Write-Section 'What went wrong' $block $MaxLines
}

Write-Section 'Exception' $causes 12
Write-Section 'Build result' $result 5

$summary = if ($result.Count -gt 0) { $result[-1].Trim() } else { "BUILD exit=$exitCode" }
Write-Host ""
if ($exitCode -eq 0) {
    Write-Host ("OK  " + $summary) -ForegroundColor Green
} else {
    Write-Host ("FAIL  " + $summary) -ForegroundColor Red
}
Write-Host ("log: " + $logPath) -ForegroundColor DarkGray
exit $exitCode
