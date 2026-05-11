import pandas as pd

# Load the Excel file
file_path = 'testingwe12.xlsx'
sheet_name = 'Sheet4'

print(f"Reading {file_path}, Sheet: {sheet_name}")
print("="*80)

try:
    # Read the Excel file
    df = pd.read_excel(file_path, sheet_name=sheet_name)
    
    print("\nColumn Headers:")
    for i, col in enumerate(df.columns):
        print(f"  {i}: {col}")
    
    print("\n" + "="*80)
    print("Searching for Requirement ID: SA_TS_3")
    print("="*80 + "\n")
    
    # Search for SA_TS_3 in all columns
    mask = df.apply(lambda row: row.astype(str).str.contains('SA_TS_3', case=False, na=False).any(), axis=1)
    found_rows = df[mask]
    
    if not found_rows.empty:
        print(f"Found {len(found_rows)} row(s) with SA_TS_3:\n")
        for idx, row in found_rows.iterrows():
            print(f"Row {idx + 2}:")  # +2 because Excel is 1-indexed and has header
            for col in df.columns:
                if pd.notna(row[col]) and str(row[col]).strip():
                    print(f"  {col}: {row[col]}")
            print()
    else:
        print("No rows found with SA_TS_3")
        print("\nShowing first 5 rows to help identify the data:")
        print(df.head().to_string())
        
except Exception as e:
    print(f"Error: {e}")
