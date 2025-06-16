import requests
import time
import os
import json
from datetime import datetime

# URL de l'endpoint de votre serveur Ktor qui renvoie les repas du jour.
# C'est la même base que pour l'application, mais on utilise 'localhost' car
# le script tourne sur le même PC que le serveur.
API_URL = "http://localhost:8080/meals/today"

def clear_screen():
    """Efface l'écran du terminal pour un affichage propre."""
    # 'nt' est pour Windows, 'posix' pour Linux/macOS
    os.system('cls' if os.name == 'nt' else 'clear')

def format_record(record):
    """Met en forme un enregistrement de repas pour un affichage lisible."""
    person_type = record.get('personType', 'N/A').upper()
    name = f"{record.get('firstName', '')} {record.get('name', '')}"
    confirmed = "OUI" if record.get('mealConfirmed', False) else "NON"
    
    details = f"Présent: {confirmed}"
    if person_type == 'RESIDENT':
        texture = record.get('mealTexture', 'N/A')
        meal_type = record.get('mealType', 'aucun').upper()
        allergies = ", ".join(record.get('allergies', [])) or "Aucune"
        details += f" | Régime: {meal_type} | Texture: {texture} | Allergies: {allergies}"

    return f"  - {name:<25} ({person_type}) | {details}"


def fetch_and_display_data():
    """
    Récupère les données depuis le serveur Ktor, les traite et les affiche.
    """
    try:
        # Fait une requête GET à l'API du serveur
        response = requests.get(API_URL, timeout=2) # Timeout de 2 secondes
        
        clear_screen()
        print("--- Moniteur de la Table 'meal_records' ---")
        print(f"Dernière mise à jour : {datetime.now().strftime('%H:%M:%S')}")
        print("-" * 50)

        # Vérifie si la requête a réussi
        if response.status_code == 200:
            records = response.json()
            
            if not records:
                print("Aucun enregistrement de repas pour aujourd'hui.")
            else:
                print(f"{len(records)} enregistrement(s) trouvé(s) :\n")
                for record in records:
                    print(format_record(record))
        else:
            print(f"Erreur du serveur : Statut {response.status_code}")
            print(f"Réponse : {response.text}")

    except requests.exceptions.RequestException as e:
        clear_screen()
        print("--- Moniteur de la Table 'meal_records' ---")
        print(f"Dernière mise à jour : {datetime.now().strftime('%H:%M:%S')}")
        print("-" * 50)
        print("\nERREUR : Impossible de se connecter au serveur Ktor.")
        print("Veuillez vous assurer que le serveur 'MealManagementBackend' est bien lancé.")
        print(f"Détail : {e}")


if __name__ == "__main__":
    print("Lancement du script de monitoring...")
    print("Assurez-vous d'avoir installé la librairie 'requests' avec 'pip install requests'")
    time.sleep(2)
    
    while True:
        fetch_and_display_data()
        time.sleep(3) # Attend 3 secondes avant de rafraîchir


