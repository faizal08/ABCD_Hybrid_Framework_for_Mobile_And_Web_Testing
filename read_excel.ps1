$excelPath = "testingwe12.xlsx"
$sheetName = "Sheet4"
$searchTerm = "SA_TS_3"

Write-Host "Reading: $excelPath - Sheet: $sheetName" -ForegroundColor Cyan
Write-Host ("=" * 80) -ForegroundColor Gray

try {
    # Create Excel COM object
    $excel = New-Object -ComObject Excel.Application
    $excel.Visible = $false
    $excel.DisplayAlerts = $false
    
    # Open workbook
    $workbook = $excel.Workbooks.Open("$PWD\$excelPath")
    $worksheet = $workbook.Worksheets.Item($sheetName)
    
    # Get used range
    $usedRange = $worksheet.UsedRange
    $rowCount = $usedRange.Rows.Count
    $colCount = $usedRange.Columns.Count
    
    # Print headers
    Write-Host "`nColumn Headers:" -ForegroundColor Yellow
    for ($col = 1; $col -le $colCount; $col++) {
        $header = $worksheet.Cells.Item(1, $col).Text
        if ($header) {
            $colLetter = [char](64 + $col)
            Write-Host "  Column $colLetter ($col): $header" -ForegroundColor White
        }
    }
    
    Write-Host "`n$("=" * 80)" -ForegroundColor Gray
    Write-Host "Searching for Requirement ID: $searchTerm" -ForegroundColor Cyan
    Write-Host ("=" * 80) -ForegroundColor Gray
    Write-Host ""
    
    # Search for SA_TS_3
    $found = $false
    for ($row = 2; $row -le $rowCount; $row++) {
        $rowContainsSearch = $false
        
        # Check if any cell in this row contains the search term
        for ($col = 1; $col -le $colCount; $col++) {
            $cellValue = $worksheet.Cells.Item($row, $col).Text
            if ($cellValue -like "*$searchTerm*") {
                $rowContainsSearch = $true
                break
            }
        }
        
        if ($rowContainsSearch) {
            $found = $true
            Write-Host "Row $row" -ForegroundColor Green
            Write-Host ("-" * 80) -ForegroundColor Gray
            
            # Print all non-empty cells in this row
            for ($col = 1; $col -le $colCount; $col++) {
                $header = $worksheet.Cells.Item(1, $col).Text
                $value = $worksheet.Cells.Item($row, $col).Text
                
                if ($header -and $value) {
                    Write-Host "  $header : " -NoNewline -ForegroundColor Yellow
                    Write-Host "$value" -ForegroundColor White
                }
            }
            Write-Host ""
        }
    }
    
    if (-not $found) {
        Write-Host "No rows found with '$searchTerm'" -ForegroundColor Red
        Write-Host "`nShowing first 3 data rows:" -ForegroundColor Yellow
        
        for ($row = 2; $row -le [Math]::Min(4, $rowCount); $row++) {
            Write-Host "`nRow $row" -ForegroundColor Cyan
            for ($col = 1; $col -le $colCount; $col++) {
                $header = $worksheet.Cells.Item(1, $col).Text
                $value = $worksheet.Cells.Item($row, $col).Text
                
                if ($header -and $value) {
                    Write-Host "  $header : $value"
                }
            }
        }
    }
    
    # Cleanup
    $workbook.Close($false)
    $excel.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($worksheet) | Out-Null
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($workbook) | Out-Null
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
    
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
    if ($excel) {
        $excel.Quit()
    }
}
