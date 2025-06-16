import requests
import time
import os
import tkinter as tk
from tkinter import ttk, font
from collections import defaultdict
from datetime import datetime
import threading

# URLs des endpoints du serveur Ktor
RESIDENTS_API_URL = "http://localhost:8080/residents"
MEALS_API_URL = "http://localhost:8080/meals/today"

class MealMonitorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Récapitulatif de Production des Repas")
        self.root.geometry("800x600")
        self.root.configure(bg='#2e2e2e')

        # --- Styles ---
        style = ttk.Style(self.root)
        style.theme_use("clam")
        
        # Style pour les cadres
        style.configure("TFrame", background="#2e2e2e")
        
        # Style pour les labels
        style.configure("TLabel", background="#2e2e2e", foreground="white", font=("Helvetica", 10))
        style.configure("Header.TLabel", font=("Helvetica", 16, "bold"), padding=(0, 10, 0, 10))
        style.configure("Total.TLabel", font=("Helvetica", 18, "bold"), foreground="#4CAF50", padding=(0, 5, 0, 10))
        style.configure("Error.TLabel", font=("Helvetica", 12, "bold"), foreground="#F44336")

        # Style pour le Treeview
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
        
        self.header_label = ttk.Label(self.main_frame, text="Récapitulatif de Production des Repas", style="Header.TLabel")
        self.header_label.pack()

        self.last_update_label = ttk.Label(self.main_frame, text="En attente de données...")
        self.last_update_label.pack()
        
        self.total_frame = ttk.Frame(self.main_frame, style="TFrame")
        self.total_frame.pack(fill='x', pady=10)

        self.total_label = ttk.Label(self.total_frame, text="TOTAL GÉNÉRAL : --", style="Total.TLabel")
        self.total_label.pack()
        
        self.details_label = ttk.Label(self.total_frame, text="Résidents: -- | Personnel: --")
        self.details_label.pack()

        # --- Treeview pour l'affichage des détails ---
        self.tree = ttk.Treeview(self.main_frame, columns=('detail', 'count'), show='tree headings')
        self.tree.heading('#0', text='Catégorie')
        self.tree.heading('detail', text='Détail')
        self.tree.heading('count', text='Quantité')
        self.tree.column('#0', width=250, anchor='w')
        self.tree.column('detail', width=300, anchor='w')
        self.tree.column('count', width=100, anchor='center')

        self.tree.pack(expand=True, fill="both", pady=10)
        
        self.status_label = ttk.Label(self.main_frame, text="Initialisation...", style="TLabel")
        self.status_label.pack(pady=5)
        
        self.update_data()

    def fetch_data(self):
        """
        Récupère les données du serveur dans un thread séparé pour ne pas bloquer l'UI.
        """
        try:
            residents_response = requests.get(RESIDENTS_API_URL, timeout=2)
            meals_response = requests.get(MEALS_API_URL, timeout=2)
            
            residents_response.raise_for_status()
            meals_response.raise_for_status()
            
            return {
                "all_residents": residents_response.json(),
                "todays_records": meals_response.json(),
                "error": None
            }
        except requests.exceptions.RequestException as e:
            return {"error": str(e)}

    def process_and_update_ui(self, data):
        """
        Met à jour l'interface utilisateur avec les données récupérées.
        """
        if data["error"]:
            self.total_label.config(text="ERREUR DE CONNEXION")
            self.details_label.config(text="Vérifiez que le serveur est bien lancé.")
            self.status_label.config(text=f"Détail : {data['error']}", style="Error.TLabel")
            # Efface les anciennes données
            for i in self.tree.get_children():
                self.tree.delete(i)
            return
            
        all_residents = data["all_residents"]
        todays_records = data["todays_records"]
        
        # Logique de calcul
        absent_resident_ids = {
            record['personId'] for record in todays_records 
            if record.get('personType') == 'resident' and not record.get('mealConfirmed')
        }
        present_residents = [res for res in all_residents if res['id'] not in absent_resident_ids]
        present_staff_count = len([
            record for record in todays_records 
            if record.get('personType') == 'staff' and record.get('mealConfirmed')
        ])
        total_meals = len(present_residents) + present_staff_count

        # Logique de groupement
        grouped_meals = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))
        for resident in present_residents:
            regime = resident.get('mealType', 'aucun').upper()
            allergies = ", ".join(sorted(resident.get('allergies', []))) or "Aucune"
            texture = resident.get('mealTexture', 'normal')
            grouped_meals[regime][allergies][texture] += 1
        
        # Mise à jour des labels
        self.last_update_label.config(text=f"Dernière mise à jour : {datetime.now().strftime('%H:%M:%S')}")
        self.total_label.config(text=f"TOTAL GÉNÉRAL DES REPAS À PRÉPARER : {total_meals}")
        self.details_label.config(text=f"Résidents: {len(present_residents)} / {len(all_residents)} | Personnel: {present_staff_count}")
        self.status_label.config(text="Connecté au serveur.", style="TLabel")

        # Mise à jour du Treeview
        for i in self.tree.get_children():
            self.tree.delete(i)

        for regime, allergy_map in sorted(grouped_meals.items()):
            regime_id = self.tree.insert('', 'end', text=f"RÉGIME : {regime}", open=True)
            for allergy, texture_map in sorted(allergy_map.items()):
                allergy_id = self.tree.insert(regime_id, 'end', text=f"Allergie(s): {allergy}", open=True)
                for texture, count in sorted(texture_map.items()):
                    self.tree.insert(allergy_id, 'end', values=(f"Texture {texture}", count))

    def update_data(self):
        """
        Lance la récupération des données et planifie la prochaine mise à jour.
        """
        # Exécute la requête réseau dans un thread pour ne pas geler l'interface
        thread = threading.Thread(target=lambda: self.process_and_update_ui(self.fetch_data()))
        thread.start()
        
        # Planifie la prochaine mise à jour dans 3 secondes
        self.root.after(3000, self.update_data)


if __name__ == "__main__":
    print("Lancement de l'application de monitoring...")
    print("Assurez-vous d'avoir installé la librairie 'requests' avec 'pip install requests'")
    
    root = tk.Tk()
    app = MealMonitorApp(root)
    root.mainloop()


