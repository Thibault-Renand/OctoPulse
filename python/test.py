import socket
import requests
import json
import time

# --- Configuration ---
HOST = '0.0.0.0'  # Écoute sur toutes les interfaces
SOCKET_PORT = 12345
KTOR_SERVER_URL = "http://localhost:8080"  # URL du serveur Ktor

def get_staff_list():
    """Récupère la liste complète du personnel depuis le serveur Ktor."""
    try:
        response = requests.get(f"{KTOR_SERVER_URL}/staff", timeout=5)
        response.raise_for_status()  # Lève une exception si la requête échoue
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"Erreur: Impossible de récupérer la liste du personnel. {e}")
        return None

def find_staff_member(staff_list, first_name, last_name):
    """Trouve un membre du personnel dans la liste par nom et prénom."""
    if staff_list is None:
        return None
    for staff in staff_list:
        if staff.get('firstName', '').lower() == first_name.lower() and \
           staff.get('name', '').lower() == last_name.lower():
            return staff
    return None

def confirm_staff_meal(staff_member, eats_on_site):
    """Envoie la confirmation de repas au serveur Ktor."""
    meal_record = {
        "personId": staff_member['id'],
        "personType": "staff",
        "name": staff_member['name'],
        "firstName": staff_member['firstName'],
        "mealConfirmed": eats_on_site,
        "date": "", # Le serveur Ktor gérera la date actuelle
        "allergies": [],
        "mealTexture": "normal",
        "mealType": "aucun"
    }
    
    try:
        headers = {'Content-Type': 'application/json'}
        response = requests.post(f"{KTOR_SERVER_URL}/meals", data=json.dumps(meal_record), headers=headers, timeout=5)
        response.raise_for_status()
        print(f"Confirmation enregistrée pour {staff_member['firstName']} {staff_member['name']}: {'Présent' if eats_on_site else 'Absent'}")
        return True
    except requests.exceptions.RequestException as e:
        print(f"Erreur lors de l'enregistrement de la confirmation pour {staff_member['firstName']}: {e}")
        return False

def process_data(data_string):
    """
    Traite une ligne de données reçue, la compare à la DB et met à jour le repas.
    """
    if data_string.count("|") != 2:
        print(f"Format de données invalide reçu : {data_string}")
        return

    first_name, last_name, response = data_string.split("|")
    first_name = first_name.strip()
    last_name = last_name.strip()
    eats_on_site = response.strip() == '1'

    print(f"\nDonnées reçues : {first_name} {last_name}, Réponse: {response.strip()}")

    # 1. Récupérer la liste du personnel à jour
    staff_list = get_staff_list()
    if staff_list is None:
        print("Impossible de continuer sans la liste du personnel.")
        return

    # 2. Vérifier si le membre du personnel existe
    staff_member = find_staff_member(staff_list, first_name, last_name)

    if staff_member:
        print(f"Membre du personnel trouvé : {staff_member['id']}")
        # 3. Mettre à jour la table des repas
        confirm_staff_meal(staff_member, eats_on_site)
    else:
        print(f"Membre du personnel non trouvé : {first_name} {last_name}. Logique d'ajout à implémenter si nécessaire.")
        # NOTE: Pour ajouter un nouveau membre, il faudrait un endpoint POST sur /staff dans Ktor.
        # Pour l'instant, on ne fait rien.

def start_socket_server():
    """Démarre le serveur socket pour écouter les connexions entrantes."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, SOCKET_PORT))
        s.listen()
        print(f"Serveur d'écoute démarré sur le port {SOCKET_PORT}...")
        while True:
            conn, addr = s.accept()
            with conn:
                print(f"Connecté par {addr}")
                try:
                    # Utiliser un buffer pour gérer les données partielles
                    buffer = ""
                    while True:
                        data = conn.recv(1024)
                        if not data:
                            break
                        buffer += data.decode('latin-1')
                        # Traiter les lignes complètes
                        while '\n' in buffer:
                            line, buffer = buffer.split('\n', 1)
                            process_data(line.strip())
                except Exception as e:
                    print(f"Erreur de communication avec le client: {e}")

if __name__ == "__main__":
    start_socket_server()


