import os
import subprocess

# Ensure CP is set (using the path you provided earlier)
CP = os.environ.get("CP", ".:/home/grey/jade/jade/lib/jade.jar")

def run_single_simulation():
    print("Compiling Java files...")
    subprocess.run(["javac", "-cp", CP, "*.java"], shell=True, check=True)
    
    print("Starting JADE Simulation (with GUI)...")
    # Launch with GUI and all 3 agents as an example
    cmd = f'java -cp {CP} jade.Boot -gui -agents "sim:SimulatorAgent;p1:RandomAgent(1);p2:GreedyAgent(1);p3:PlannerAgent(1)"'
    subprocess.run(cmd, shell=True)

if __name__ == "__main__":
    run_single_simulation()