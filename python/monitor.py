import requests
import time
import os
from collections import defaultdict
from datetime import datetime

# URLs des endpoints du serveur Ktor
RESIDENTS_API_URL = "http://localhost:8080/residents"
MEALS_API_URL = "http://localhost:8080/meals/today"

def clear_screen():
    """Efface l'écran du terminal pour un affichage propre."""
    os.system('cls' if os.name == 'nt' else 'clear')

def fetch_and_display_data():
    """
    Récupère les données des résidents et des repas, calcule les totaux
    et affiche le récapitulatif de production.
    """
    try:
        # Récupérer toutes les données nécessaires en une seule fois
        residents_response = requests.get(RESIDENTS_API_URL, timeout=2)
        meals_response = requests.get(MEALS_API_URL, timeout=2)

        # S'assurer que les deux requêtes ont réussi
        residents_response.raise_for_status()
        meals_response.raise_for_status()

        all_residents = residents_response.json()
        todays_records = meals_response.json()

        # Identifier les résidents qui ont explicitement dit être absents
        absent_resident_ids = {
            record['personId'] for record in todays_records 
            if record.get('personType') == 'resident' and not record.get('mealConfirmed')
        }
        
        # Filtrer pour ne garder que les résidents présents
        present_residents = [res for res in all_residents if res['id'] not in absent_resident_ids]
        
        # Compter les membres du personnel présents
        present_staff_count = len([
            record for record in todays_records 
            if record.get('personType') == 'staff' and record.get('mealConfirmed')
        ])
        
        # Calculer le total général des repas
        total_meals = len(present_residents) + present_staff_count

        # --- Logique de groupement ---
        grouped_meals = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))

        for resident in present_residents:
            regime = resident.get('mealType', 'aucun').upper()
            allergies = ", ".join(sorted(resident.get('allergies', []))) or "Aucune"
            texture = resident.get('mealTexture', 'normal')
            
            grouped_meals[regime][allergies][texture] += 1

        # --- Affichage Amélioré ---
        clear_screen()
        print("╔══════════════════════════════════════════════════════╗")
        print("║          RÉCAPITULATIF DE PRODUCTION DES REPAS         ║")
        print("╚══════════════════════════════════════════════════════╝")
        print(f"  (Dernière mise à jour : {datetime.now().strftime('%H:%M:%S')})")
        
        print("\n========================================================")
        print(f"  TOTAL GÉNÉRAL DES REPAS À PRÉPARER : {total_meals}")
        print("========================================================")
        print(f"  > Détail Résidents : {len(present_residents)} / {len(all_residents)}")
        print(f"  > Détail Personnel : {present_staff_count}")
        print("--------------------------------------------------------\n")

        if not present_residents:
            print("Aucun repas résident à préparer.")
        else:
            sorted_regimes = sorted(grouped_meals.keys())
            for regime in sorted_regimes:
                print(f"RÉGIME : {regime}")
                sorted_allergies = sorted(grouped_meals[regime].keys())
                for allergy_group in sorted_allergies:
                    print(f"  • Allergie(s) : {allergy_group}")
                    sorted_textures = sorted(grouped_meals[regime][allergy_group].keys())
                    for texture in sorted_textures:
                        count = grouped_meals[regime][allergy_group][texture]
                        print(f"    - Texture {texture:<10} : {count} repas")
                print() # Ligne vide pour la séparation

    except requests.exceptions.RequestException as e:
        clear_screen()
        print("╔══════════════════════════════════════════════════════╗")
        print("║                       ERREUR                         ║")
        print("╚══════════════════════════════════════════════════════╝")
        print(f"  (Heure de l'échec : {datetime.now().strftime('%H:%M:%S')})")
        print("\nImpossible de se connecter au serveur Ktor.")
        print("Veuillez vous assurer que le serveur 'MealManagementBackend' est bien lancé.")
        print(f"\nDétail technique : {e}")

if __name__ == "__main__":
    print("Lancement du script de monitoring...")
    print("Appuyez sur Ctrl+C pour arrêter.")
    print("Assurez-vous d'avoir installé la librairie 'requests' avec 'pip install requests'")
    time.sleep(2)
    
    try:
        while True:
            fetch_and_display_data()
            time.sleep(3) # Attend 3 secondes avant de rafraîchir
    except KeyboardInterrupt:
        print("\n\nArrêt du script.")


