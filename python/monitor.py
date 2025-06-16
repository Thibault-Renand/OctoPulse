import requests
import time
import tkinter as tk
from tkinter import ttk, font
from collections import defaultdict
from datetime import datetime
import threading
import socket

# Constantes pour la découverte réseau
DISCOVERY_PORT = 9999
DISCOVERY_KEYWORD = "poulpe"

class MealMonitorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Récapitulatif de Production des Repas")
        self.root.geometry("800x600")
        self.root.configure(bg='#2e2e2e')
        
        self.server_ip = None
        self.residents_api_url = ""
        self.meals_api_url = ""

        # --- Styles ---
        style = ttk.Style(self.root)
        style.theme_use("clam")
        
        style.configure("TFrame", background="#2e2e2e")
        
        style.configure("TLabel", background="#2e2e2e", foreground="white", font=("Helvetica", 10))
        style.configure("Header.TLabel", font=("Helvetica", 16, "bold"), padding=(0, 10, 0, 10))
        style.configure("Total.TLabel", font=("Helvetica", 18, "bold"), foreground="#4CAF50", padding=(0, 5, 0, 5))
        style.configure("Details.TLabel", font=("Helvetica", 11))
        style.configure("Staff.TLabel", font=("Helvetica", 12, "bold"), foreground="#87CEEB") # Bleu ciel pour le personnel
        style.configure("Error.TLabel", font=("Helvetica", 12, "bold"), foreground="#F44336")

        style.configure("Treeview", 
                        background="#3c3c3c", 
                        foreground="white", 
                        fieldbackground="#3c3c3c",
                        rowheight=25,
                        font=("Helvetica", 10))
        style.configure("Treeview.Heading", 
                        background="#555555", 
                        foreground="white", 
                        font=("Helvetica", 11, "bold"))
        style.map("Treeview.Heading", background=[('active', '#666666')])


        # --- Widgets ---
        self.main_frame = ttk.Frame(self.root, padding="10")
        self.main_frame.pack(expand=True, fill="both")
        
        self.header_label = ttk.Label(self.main_frame, text="Récapitulatif de Production des Repas", style="Header.TLabel", anchor="center")
        self.header_label.pack(fill='x')

        self.last_update_label = ttk.Label(self.main_frame, text="En attente de données...", anchor="center")
        self.last_update_label.pack(fill='x')
        
        self.total_frame = ttk.Frame(self.main_frame, style="TFrame")
        self.total_frame.pack(fill='x', pady=10)

        self.total_label = ttk.Label(self.total_frame, text="TOTAL GÉNÉRAL : --", style="Total.TLabel", anchor="center")
        self.total_label.pack(fill='x')
        
        self.resident_details_label = ttk.Label(self.total_frame, text="Résidents : --", style="Details.TLabel", anchor="center")
        self.resident_details_label.pack(fill='x', pady=(5,0))
        
        self.staff_details_label = ttk.Label(self.total_frame, text="Personnel : --", style="Staff.TLabel", anchor="center")
        self.staff_details_label.pack(fill='x')


        # --- Treeview pour l'affichage des détails ---
        self.tree = ttk.Treeview(self.main_frame, columns=('detail', 'count'), show='tree headings')
        self.tree.heading('#0', text='Catégorie')
        self.tree.heading('detail', text='Détail')
        self.tree.heading('count', text='Quantité')
        self.tree.column('#0', width=250, anchor='w')
        self.tree.column('detail', width=300, anchor='w')
        self.tree.column('count', width=100, anchor='center')

        self.tree.pack(expand=True, fill="both", pady=10)
        
        self.status_label = ttk.Label(self.main_frame, text="Recherche du serveur sur le réseau...", style="TLabel")
        self.status_label.pack(pady=5)
        
        self.start_monitoring()

    def discover_server(self):
        """Envoie un appel sur le réseau et attend une réponse du serveur."""
        print(f"Envoi de l'appel de découverte ('{DISCOVERY_KEYWORD}')...")
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                sock.settimeout(3) # Attend 3 secondes max
                
                sock.sendto(DISCOVERY_KEYWORD.encode(), ('<broadcast>', DISCOVERY_PORT))
                
                data, addr = sock.recvfrom(1024)
                self.server_ip = data.decode()
                print(f"Serveur trouvé à l'adresse : {self.server_ip}")
                self.residents_api_url = f"http://{self.server_ip}:8080/residents"
                self.meals_api_url = f"http://{self.server_ip}:8080/meals/today"
                return True
        except socket.timeout:
            print("Aucun serveur n'a répondu à l'appel.")
            return False
        except Exception as e:
            print(f"Erreur lors de la découverte : {e}")
            return False

    def start_monitoring(self):
        """Lance la découverte puis le rafraîchissement des données."""
        # Lancer la découverte dans un thread pour ne pas bloquer l'UI
        threading.Thread(target=self._discover_and_schedule, daemon=True).start()

    def _discover_and_schedule(self):
        if self.discover_server():
            self.root.after(0, self.update_data) # Planifie la première mise à jour dans le thread principal
        else:
            def update_ui_error():
                self.total_label.config(text="SERVEUR INTROUVABLE")
                self.resident_details_label.config(text="")
                self.staff_details_label.config(text="Vérifiez que le serveur est bien lancé sur le même réseau.")
                self.status_label.config(text="Échec de la découverte réseau.", style="Error.TLabel")
            self.root.after(0, update_ui_error)


    def fetch_data(self):
        """Récupère les données du serveur."""
        if not self.server_ip:
            return {"error": "IP du serveur non définie."}
        try:
            residents_response = requests.get(self.residents_api_url, timeout=2)
            meals_response = requests.get(self.meals_api_url, timeout=2)
            residents_response.raise_for_status()
            meals_response.raise_for_status()
            return { "all_residents": residents_response.json(), "todays_records": meals_response.json(), "error": None }
        except requests.exceptions.RequestException as e:
            return {"error": str(e)}

    def process_and_update_ui(self, data):
        """Met à jour l'interface utilisateur avec les données récupérées."""
        if data["error"]:
            self.total_label.config(text="ERREUR DE CONNEXION")
            self.resident_details_label.config(text="")
            self.staff_details_label.config(text="Vérifiez que le serveur est bien lancé.")
            self.status_label.config(text=f"Détail : {data['error']}", style="Error.TLabel")
            for i in self.tree.get_children():
                self.tree.delete(i)
            return
            
        all_residents = data["all_residents"]
        todays_records = data["todays_records"]
        
        absent_resident_ids = { r['personId'] for r in todays_records if r.get('personType') == 'resident' and not r.get('mealConfirmed') }
        present_residents = [res for res in all_residents if res['id'] not in absent_resident_ids]
        present_staff_count = len([r for r in todays_records if r.get('personType') == 'staff' and r.get('mealConfirmed')])
        total_meals = len(present_residents) + present_staff_count

        grouped_meals = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))
        for resident in present_residents:
            regime = resident.get('mealType', 'aucun').upper()
            allergies = ", ".join(sorted(resident.get('allergies', []))) or "Aucune"
            texture = resident.get('mealTexture', 'normal')
            grouped_meals[regime][allergies][texture] += 1
        
        self.last_update_label.config(text=f"Dernière mise à jour : {datetime.now().strftime('%H:%M:%S')}")
        self.total_label.config(text=f"TOTAL GÉNÉRAL DES REPAS À PRÉPARER : {total_meals}")
        self.resident_details_label.config(text=f"Résidents : {len(present_residents)} / {len(all_residents)}")
        self.staff_details_label.config(text=f"Personnel : {present_staff_count}")
        self.status_label.config(text=f"Connecté au serveur ({self.server_ip})", style="TLabel")

        for i in self.tree.get_children():
            self.tree.delete(i)

        for regime, allergy_map in sorted(grouped_meals.items()):
            regime_id = self.tree.insert('', 'end', text=f"RÉGIME : {regime}", open=True)
            for allergy, texture_map in sorted(allergy_map.items()):
                allergy_id = self.tree.insert(regime_id, 'end', text=f"Allergie(s): {allergy}", open=True)
                for texture, count in sorted(texture_map.items()):
                    self.tree.insert(allergy_id, 'end', values=(f"Texture {texture}", count))

    def update_data(self):
        """Lance la récupération des données et planifie la prochaine mise à jour."""
        threading.Thread(target=lambda: self.root.after(0, self.process_and_update_ui, self.fetch_data()), daemon=True).start()
        
        self.root.after(3000, self.update_data)


if __name__ == "__main__":
    print("Lancement de l'application de monitoring...")
    print("Assurez-vous d'avoir installé la librairie 'requests' avec 'pip install requests'")
    
    root = tk.Tk()
    app = MealMonitorApp(root)
    root.mainloop()

