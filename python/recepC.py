import socket
import time

host = '0.0.0.0'
port = 12345

def enregistrer_donnees(prenom, nom, reponse):
    with open("/home/chef/data.txt", "a") as f:  # mode append
        f.write(f"Prénom : {prenom}\n")
        f.write(f"Nom : {nom}\n")
        f.write(f"Réponse : {reponse}\n")

while True:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
            server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server_socket.bind((host, port))
            server_socket.listen(1)

            client_socket, client_address = server_socket.accept()

            with client_socket:
                with client_socket.makefile("r", encoding="latin-1") as flux:
                    for ligne in flux:
                        texte = ligne.strip()
                        if texte.count("|") == 2:
                            prenom, nom, reponse = texte.split("|")
                            enregistrer_donnees(prenom, nom, reponse)

                        time.sleep(1)

    except:
        time.sleep(2)
