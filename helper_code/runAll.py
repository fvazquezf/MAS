import os
import subprocess
import re

CP = os.environ.get("CP")
if not CP:
    print("[!] ERROR: CP environment variable is not set!")
    exit(1)

RESULTS_DIR = "results"

SCENARIOS = {
    "fixed_0_traps": (0, 1000),
    "fixed_10_traps": (10, 1000),
    "dynamic_0_traps": (0, 10),
    "dynamic_10_traps": (10, 10)
}

SETUPS = {
    "Random_alone_1": (1, "p_random:RandomAgent(1)"),
    "Random_alone_20": (1, "p_random:RandomAgent(20)"),
    "Greedy_alone_1": (1, "p_greedy:GreedyAgent(1)"),
    "Greedy_alone_20": (1, "p_greedy:GreedyAgent(20)"),
    "Planner_alone_1": (1, "p_planner:PlannerAgent(1)"),
    "Planner_alone_20": (1, "p_planner:PlannerAgent(20)"),
    "All3_together_1": (3, "p_random:RandomAgent(1);p_greedy:GreedyAgent(1);p_planner:PlannerAgent(1)"),
    "All3_together_20": (3, "p_random:RandomAgent(20);p_greedy:GreedyAgent(20);p_planner:PlannerAgent(20)")
}

def prepare_simulator_code(traps, redist, participants):
    with open("SimulatorAgent.java", "r") as f:
        code = f.read()
    
    code = re.sub(r'int numTraps\s*=\s*\d+;', f'int numTraps = {traps};', code)
    code = re.sub(r'int numStepsMapReDist\s*=\s*\d+;', f'int numStepsMapReDist = {redist};', code)
    code = re.sub(r'int numParticipants\s*=\s*\d+;', f'int numParticipants = {participants};', code)
    
    if "System.exit(0);" not in code:
        code = re.sub(r'(doDelete\(\);)', r'\1\n                    System.exit(0);', code)
        
    with open("SimulatorAgent.java", "w") as f:
        f.write(code)

def run_all_experiments():
    if not os.path.exists(RESULTS_DIR):
        os.makedirs(RESULTS_DIR)

    print("Starting Automated Experiment Pipeline (320 runs)...")
    
    current_port = 1100 

    for scen_name, (traps, redist) in SCENARIOS.items():
        print(f"\n================ SCENARIO: {scen_name} ================")
        
        for setup_name, (participants, agents_str) in SETUPS.items():
            print(f"Running Setup: {setup_name}")
            
            prepare_simulator_code(traps, redist, participants)
            
            comp_result = subprocess.run(f"javac -cp {CP} *.java", shell=True, capture_output=True, text=True)
            if comp_result.returncode != 0:
                print("\n[!] COMPILATION ERROR! Please check your Java code:")
                print(comp_result.stderr)
                return

            for rep in range(1, 11):
                print(f"  -> Repetition {rep}/10 (Port {current_port})...", end="", flush=True)
                
                filename = f"{RESULTS_DIR}/{scen_name}__{setup_name}__rep{rep}.asp"
                
                cmd = f'java -cp {CP} jade.Boot -port {current_port} -agents "sim:SimulatorAgent;{agents_str}"'
                
                with open(filename, "w") as f:
                    try:
                        subprocess.run(cmd, shell=True, stdout=f, stderr=subprocess.STDOUT, timeout=15)
                        print(" Done.")
                    except subprocess.TimeoutExpired:
                        print(f" TIMEOUT! (Agent got stuck. Check {filename})")
                
                current_port += 1

    print("\nAll experiments completed successfully!")

if __name__ == "__main__":
    run_all_experiments()