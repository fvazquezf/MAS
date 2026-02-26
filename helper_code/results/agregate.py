import os
import glob
from collections import defaultdict

RESULTS_DIR = "/home/grey/USC/MAS/MAS/helper_code/results/"
OUTPUT_FILE = "aggregated_results.txt"

def parse_results():
    # Data structure: data[scenario][setup][agent_type] = list of values
    scores_data = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    traps_data = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))

    # Find all generated .asp files
    filepaths = glob.glob(os.path.join(RESULTS_DIR, "*.asp"))
    
    if not filepaths:
        print(f"No .asp files found in '{RESULTS_DIR}'. Make sure the simulations finished.")
        return

    print(f"Found {len(filepaths)} result files. Extracting data...")

    for filepath in filepaths:
        filename = os.path.basename(filepath)
        # Expected filename format: fixed_0_traps__Random_alone_1__rep1.asp
        parts = filename.replace(".asp", "").split("__")
        if len(parts) != 3: 
            continue

        scenario = parts[0]
        setup = parts[1]
        
        with open(filepath, "r") as f:
            content = f.read()

        # Split the output by "Name: " to isolate each agent's final status block
        agent_blocks = content.split("Name: ")[1:]
        
        for block in agent_blocks:
            lines = block.strip().split('\n')
            if not lines: continue
            
            raw_name = lines[0].strip()

            # Map the raw agent names to the categories needed for the report table
            if "random" in raw_name.lower(): 
                a_type = "Random Agent"
            elif "greedy" in raw_name.lower(): 
                a_type = "Agent Type A (Greedy)"
            elif "planner" in raw_name.lower(): 
                a_type = "Agent Type B (Planner)"
            else: 
                a_type = raw_name

            score = None
            traps = None

            # Extract the numerical values from the block
            for line in lines:
                if line.startswith("Score:"):
                    try:
                        score = int(line.split(":")[1].strip())
                    except ValueError:
                        pass
                elif line.startswith("NumTraps:"):
                    try:
                        traps = int(line.split(":")[1].strip())
                    except ValueError:
                        pass

            # Save the values to our lists
            if score is not None:
                scores_data[scenario][setup][a_type].append(score)
            if traps is not None:
                traps_data[scenario][setup][a_type].append(traps)

    # Write the aggregated data to a text file
    with open(OUTPUT_FILE, "w") as out:
        out.write("====================================================\n")
        out.write("      MAS LAB 1 - AGGREGATED SIMULATION RESULTS     \n")
        out.write("====================================================\n\n")

        # Order the scenarios to match the lab assignment PDF
        scenarios_order = [
            "fixed_0_traps", 
            "fixed_10_traps", 
            "dynamic_0_traps", 
            "dynamic_10_traps"
        ]

        for sc in scenarios_order:
            if sc not in scores_data: 
                continue
                
            out.write(f"### {sc.replace('_', ' ').upper()} ###\n")

            for setup, agents in scores_data[sc].items():
                out.write(f"  Setup: {setup}\n")
                
                for agent, scores_list in agents.items():
                    avg_score = sum(scores_list) / len(scores_list)
                    out.write(f"    - {agent}:\n")
                    out.write(f"        Average Score: {avg_score:.2f} (from {len(scores_list)} runs)\n")

                    # Include average traps hit if available (useful for the Discussion section)
                    if agent in traps_data[sc][setup]:
                        t_list = traps_data[sc][setup][agent]
                        avg_traps = sum(t_list) / len(t_list)
                        out.write(f"        Average Traps Hit: {avg_traps:.2f}\n")
                out.write("\n")
            out.write("-" * 50 + "\n\n")

    print(f"\nExtraction complete! All averages have been saved to '{OUTPUT_FILE}'.")
    print("You can copy these numbers directly into the tables for your PDF report.")

if __name__ == "__main__":
    parse_results()