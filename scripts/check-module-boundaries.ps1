#!/usr/bin/env pwsh
# Проверка инвариантов модульной структуры InfCanvas (фаза 2).
# Возвращает exit code 1, если границы нарушены.

$ErrorActionPreference = 'Stop'
$violations = @()

function Check-NoImports {
    param([string]$Path, [string]$Pattern, [string]$Reason)
    if (-not (Test-Path $Path)) { return }
    Get-ChildItem -Recurse -Filter *.kt -Path $Path -ErrorAction SilentlyContinue |
        Select-String -Pattern $Pattern |
        ForEach-Object {
            $script:violations += "$($_.Path):$($_.LineNumber): $Reason`n    $($_.Line.Trim())"
        }
}

# 1. core:domain не должен импортировать ничего из :app/:core:data/:core:canvas/feature
Check-NoImports -Path 'core/domain/src' `
    -Pattern 'import com\.infinitecanvas\.board\.(data|presentation|feature)\.' `
    -Reason 'core:domain must not depend on data/presentation/feature'

# 2. core:data не зависит от presentation/feature
Check-NoImports -Path 'core/data/src' `
    -Pattern 'import com\.infinitecanvas\.board\.(presentation|feature)\.' `
    -Reason 'core:data must not depend on presentation/feature'

# 3. presentation не импортирует core:data (только порты)
Check-NoImports -Path 'app/src/main/java/com/infinitecanvas/board/presentation' `
    -Pattern 'import com\.infinitecanvas\.board\.data\.' `
    -Reason 'presentation must use domain ports, not concrete data classes'

# 4. В :app core:data импортирует ТОЛЬКО AppContainer
$appImports = Get-ChildItem -Recurse -Filter *.kt -Path 'app/src/main/java' -ErrorAction SilentlyContinue |
    Select-String -Pattern 'import com\.infinitecanvas\.board\.data\.' |
    ForEach-Object { $_.Path } | Sort-Object -Unique
$allowed = @('app\src\main\java\com\infinitecanvas\board\di\AppContainer.kt') |
    ForEach-Object { (Resolve-Path $_ -ErrorAction SilentlyContinue).Path }
foreach ($p in $appImports) {
    if ($allowed -notcontains $p) {
        $violations += "$p imports core:data; only AppContainer.kt is allowed"
    }
}

if ($violations.Count -gt 0) {
    Write-Host "Module boundary violations:" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

Write-Host "Module boundaries OK" -ForegroundColor Green
exit 0