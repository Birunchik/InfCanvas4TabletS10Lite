#!/usr/bin/env pwsh
# Проверка инвариантов модульной структуры InfCanvas (фазы 4–5).
# Возвращает exit code 1, если границы нарушены.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$violations = @()

# Модуль → namespace и разрешённые project-зависимости (включая test-only).
$modules = @(
    @{ Path = 'app';              Namespace = 'com.infinitecanvas.board';           Allowed = @(':core:domain', ':core:data', ':core:designsystem', ':feature:board', ':feature:home') },
    @{ Path = 'core/domain';      Namespace = 'com.infinitecanvas.board.domain';    Allowed = @() },
    @{ Path = 'core/data';        Namespace = 'com.infinitecanvas.board.data';      Allowed = @(':core:domain') },
    @{ Path = 'core/canvas';      Namespace = 'com.infinitecanvas.canvas';          Allowed = @(':core:domain', ':core:testing') },
    @{ Path = 'core/designsystem'; Namespace = 'com.infinitecanvas.designsystem';   Allowed = @() },
    @{ Path = 'core/testing';     Namespace = 'com.infinitecanvas.testing';         Allowed = @(':core:domain') },
    @{ Path = 'feature/board';    Namespace = 'com.infinitecanvas.feature.board';   Allowed = @(':core:domain', ':core:canvas', ':core:testing') },
    @{ Path = 'feature/home';     Namespace = 'com.infinitecanvas.feature.home';    Allowed = @(':core:domain', ':core:designsystem', ':core:testing') }
)

function Add-Violation {
    param([string]$Message)
    $script:violations += $Message
}

function Get-KotlinFiles {
    param([string]$ModulePath)
    $src = Join-Path $root (Join-Path $ModulePath 'src')
    if (-not (Test-Path $src)) { return @() }
    return @(Get-ChildItem -Recurse -Filter *.kt -Path $src -ErrorAction SilentlyContinue)
}

function Check-NoImports {
    param([string]$ModulePath, [string]$Pattern, [string]$Reason)
    foreach ($file in (Get-KotlinFiles -ModulePath $ModulePath)) {
        Select-String -Path $file.FullName -Pattern $Pattern -ErrorAction SilentlyContinue |
            ForEach-Object {
                Add-Violation ("{0}:{1}: {2}`n    {3}" -f $_.Path, $_.LineNumber, $Reason, $_.Line.Trim())
            }
    }
}

# 1. Объявленные project-зависимости не выходят за разрешённый граф.
foreach ($module in $modules) {
    $buildFile = Join-Path $root (Join-Path $module.Path 'build.gradle.kts')
    if (-not (Test-Path $buildFile)) {
        Add-Violation ("{0}: missing build.gradle.kts" -f $module.Path)
        continue
    }
    $text = Get-Content -Raw -Path $buildFile
    $depMatches = [regex]::Matches($text, 'project\("(:[^"]+)"\)')
    $seen = @{}
    foreach ($m in $depMatches) {
        $dep = $m.Groups[1].Value
        if ($seen.ContainsKey($dep)) { continue }
        $seen[$dep] = $true
        if ($module.Allowed -notcontains $dep) {
            Add-Violation ("{0}/build.gradle.kts: forbidden project dependency {1}" -f $module.Path, $dep)
        }
    }
}

# 2. Domain не зависит от Android/Compose, кроме Uri в портах.
Check-NoImports -ModulePath 'core/domain' -Pattern '^import android\.(?!net\.Uri)' -Reason 'core:domain must not depend on Android framework'
Check-NoImports -ModulePath 'core/domain' -Pattern '^import androidx\.' -Reason 'core:domain must not depend on AndroidX/Compose'
Check-NoImports -ModulePath 'core/domain' -Pattern '^import com\.infinitecanvas\.(board\.(data|presentation|feature)|canvas|feature|designsystem)\.' -Reason 'core:domain must not depend on other layers/modules'

# 3. Data не знает про UI/фичи/холст.
Check-NoImports -ModulePath 'core/data' -Pattern '^import com\.infinitecanvas\.(board\.presentation|canvas|feature|designsystem)\.' -Reason 'core:data must not depend on presentation/ui modules'

# 4. Canvas не знает про data и экраны.
Check-NoImports -ModulePath 'core/canvas' -Pattern '^import com\.infinitecanvas\.(board\.data|board\.presentation\.screen|feature|designsystem)\.' -Reason 'core:canvas must not depend on data/screens/features'

# 5. Design system не знает про данные и фичи.
Check-NoImports -ModulePath 'core/designsystem' -Pattern '^import com\.infinitecanvas\.(board\.data|canvas|feature)\.' -Reason 'core:designsystem must stay self-contained'

# 6. Фичи общаются только через domain/разрешённые модули.
Check-NoImports -ModulePath 'feature/board' -Pattern '^import com\.infinitecanvas\.(board\.data|board\.presentation\.screen\.home|feature\.home)\.' -Reason 'feature:board must not depend on data or feature:home'
Check-NoImports -ModulePath 'feature/home' -Pattern '^import com\.infinitecanvas\.(board\.data|board\.presentation\.screen\.board|feature\.board)\.' -Reason 'feature:home must not depend on data or feature:board'

# 7. Test-фикстуры не тянут UI.
Check-NoImports -ModulePath 'core/testing' -Pattern '^import com\.infinitecanvas\.(board\.presentation|board\.data|canvas|feature)\.' -Reason 'core:testing must stay independent of UI/data modules'

# 8. В :app только AppContainer знает про core:data.
$appDataImports = @()
foreach ($file in (Get-KotlinFiles -ModulePath 'app')) {
    $matches = Select-String -Path $file.FullName -Pattern '^import com\.infinitecanvas\.board\.data\.' -ErrorAction SilentlyContinue
    if ($matches) { $appDataImports += $file.FullName }
}
$allowedContainer = Join-Path $root 'app/src/main/java/com/infinitecanvas/board/di/AppContainer.kt'
foreach ($path in $appDataImports) {
    if ($path -ne $allowedContainer) {
        Add-Violation ("{0} imports core:data; only AppContainer.kt is allowed" -f $path)
    }
}

# 9. Никто не импортирует чужой R (разрешены свой namespace и designsystem.R).
$rPattern = '^import\s+(com\.infinitecanvas[\w.]*)\.R(\s+as\s+\w+)?\s*$'
foreach ($module in $modules) {
    foreach ($file in (Get-KotlinFiles -ModulePath $module.Path)) {
        Select-String -Path $file.FullName -Pattern $rPattern -ErrorAction SilentlyContinue |
            ForEach-Object {
                $line = $_.Line.Trim()
                $rPackage = [regex]::Match($line, $rPattern).Groups[1].Value
                if ($rPackage -ne $module.Namespace -and $rPackage -ne 'com.infinitecanvas.designsystem') {
                    Add-Violation ("{0}:{1}: foreign R ({2}); expected {3}" -f $_.Path, $_.LineNumber, $rPackage, $module.Namespace)
                }
            }
    }
}

# 10. Namespace'ы уникальны (иначе дубли R-классов).
$byNamespace = $modules | Group-Object { $_.Namespace }
foreach ($group in $byNamespace) {
    if ($group.Count -gt 1) {
        Add-Violation ("namespace '{0}' is used by several modules: {1}" -f $group.Name, (($group.Group | ForEach-Object { $_.Path }) -join ', '))
    }
}

if ($violations.Count -gt 0) {
    Write-Host 'Module boundary violations:' -ForegroundColor Red
    $violations | ForEach-Object { Write-Host ("  " + $_) -ForegroundColor Red }
    exit 1
}

Write-Host 'Module boundaries OK' -ForegroundColor Green
exit 0
