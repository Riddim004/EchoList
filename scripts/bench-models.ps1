# 模型横评：同一任务解析考题，测延迟 + 工具调用质量
# 用法: .\scripts\bench-models.ps1
$ErrorActionPreference = "Stop"
$key = (Select-String -Path "$PSScriptRoot\..\local.properties" -Pattern 'GLM_API_KEY=(.+)').Matches.Groups[1].Value.Trim()

$models = @("glm-5.2", "glm-5-turbo", "glm-5", "glm-4.7", "glm-4.5-air", "glm-4.7-flash")
$runs = 2

$bodyTemplate = @'
{"model":"__MODEL__","thinking":{"type":"enabled"},"temperature":0.1,"messages":[{"role":"system","content":"你是任务管理助手，用户描述任务时必须调用create_task工具。当前时间2026-07-29T13:30:00+08:00（周三）"},{"role":"user","content":"明天下午三点开产品评审会，周六记得买牛奶"}],"tools":[{"type":"function","function":{"name":"create_task","description":"创建任务","parameters":{"type":"object","properties":{"title":{"type":"string"},"category":{"type":"string","enum":["WORK","LIFE"]},"remind_time":{"type":"string","description":"ISO8601"}},"required":["title","category"]}}}],"tool_choice":"auto"}
'@

$results = @()
foreach ($m in $models) {
    $latencies = @()
    $callCount = -1
    $times = ""
    $tokens = ""
    $err = ""
    for ($i = 1; $i -le $runs; $i++) {
        $body = $bodyTemplate.Replace("__MODEL__", $m)
        $bytes = [Text.Encoding]::UTF8.GetBytes($body)
        $ok = $false
        foreach ($attempt in 1..3) {
            try {
                $sw = [Diagnostics.Stopwatch]::StartNew()
                $resp = Invoke-RestMethod -Uri "https://open.bigmodel.cn/api/paas/v4/chat/completions" `
                    -Method Post -Headers @{Authorization = "Bearer $key"} `
                    -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 120
                $sw.Stop()
                $latencies += [math]::Round($sw.Elapsed.TotalSeconds, 1)
                $tc = $resp.choices[0].message.tool_calls
                $callCount = if ($tc) { @($tc).Count } else { 0 }
                if ($tc) {
                    $times = (@($tc) | ForEach-Object {
                        ($_.function.arguments | ConvertFrom-Json).remind_time
                    }) -join " | "
                }
                $tokens = "$($resp.usage.prompt_tokens)+$($resp.usage.completion_tokens)(r$($resp.usage.completion_tokens_details.reasoning_tokens))"
                $ok = $true
                break
            } catch {
                $err = $_.Exception.Message
                Write-Host "  [$m run$i attempt$attempt] $err"
                Start-Sleep (5 * $attempt)  # 限流退避后重试
            }
        }
        if (-not $ok) { $err = "FAILED"; break }
        Start-Sleep 2
    }
    $avg = if ($latencies.Count -gt 0) { [math]::Round(($latencies | Measure-Object -Average).Average, 1) } else { -1 }
    $results += [PSCustomObject]@{
        Model     = $m
        AvgSec    = $avg
        Runs      = ($latencies -join "/")
        ToolCalls = $callCount
        Times     = $times
        Tokens    = $tokens
        Err       = $err
    }
    Write-Host "done: $m -> ${avg}s calls=$callCount"
}

Write-Host "`n===== RESULT ====="
$results | Format-Table -AutoSize | Out-String -Width 300 | Write-Host
