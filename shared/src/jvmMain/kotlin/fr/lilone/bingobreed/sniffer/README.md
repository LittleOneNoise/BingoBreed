# Sniffer réseau Dofus 3

Capture passive du trafic Dofus pour lecture des échanges (parsing protobuf à venir).

## Prérequis

- **Npcap** installé sur Windows (mode WinPcap-compatible) — requis par pcap4j.
  Sans lui, `Pcaps.findAllDevs()` ne renvoie rien.
- L'app doit tourner avec des droits suffisants pour la capture (Npcap installé
  en mode "admin only" => lancer en administrateur).

## Flux de fonctionnement

```
SnifferEngine.start()
  │
  ├─(1) NetworkInterfaceDetector.detect()
  │       socket UDP 8.8.8.8:10002 → IP locale → interface pcap correspondante
  │
  ├─(2) DofusConfigProvider.fetchConnectionHosts()
  │       GET https://dofus2.cdn.ankama.com/config/dofus3.json
  │       → champ connectionHosts ["JMBouftou:host:5555,443", ...]
  │       → HostResolver : host → IPs (load balancer Ankama), port 5555 (non chiffré)
  │
  └─(3) Écoutes coroutine (SupervisorJob)
          ConnectionServerListener  ← long-lived, ne s'arrête jamais
              │  détecte le serveur de jeu sélectionné (parser protobuf — TODO)
              ▼
          GameServerListener(serveurA)   ┐
          GameServerListener(serveurB)   ├─ frères, concurrents
          ...                            ┘  un par host, lancés à la volée
```

L'écoute du serveur de connexion **continue de tourner** pendant les écoutes de
serveurs de jeu : si le joueur change de serveur, un nouveau `GameServerListener`
est lancé sans interrompre les autres.

## Points d'extension (TODO protobuf)

- `parser/ConnectionMessageInterpreter` : reassembly TCP + décodage des messages
  du serveur de connexion pour extraire l'adresse du serveur de jeu choisi.
  (`NoOpConnectionMessageInterpreter` est le stub courant.)
- `listener/GameServerListener` : décodage des messages de jeu.

## Utilisation

```kotlin
val engine = SnifferEngine()
scope.launch { engine.events.collect { event -> /* UI / logs */ } }
engine.start()
// ...
engine.stop()
```
