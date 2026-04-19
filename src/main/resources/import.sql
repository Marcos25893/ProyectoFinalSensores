/*M!999999\- enable the sandbox mode */
-- MariaDB dump 10.19-12.0.2-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: localhost    Database: springapi
-- ------------------------------------------------------
-- Server version	12.0.2-MariaDB-ubu2404

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*M!100616 SET @OLD_NOTE_VERBOSITY=@@NOTE_VERBOSITY, NOTE_VERBOSITY=0 */;

--
-- Table structure for table `lecturas`
--

DROP TABLE IF EXISTS `lecturas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturas` (
  `valor` double DEFAULT NULL,
  `fecha_hora` datetime(6) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `sensor_id` bigint(20) NOT NULL,
  `unidad` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKawx5iy29jocdow29b0skr8c41` (`sensor_id`),
  CONSTRAINT `FKawx5iy29jocdow29b0skr8c41` FOREIGN KEY (`sensor_id`) REFERENCES `sensores` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecturas`
--

LOCK TABLES `lecturas` WRITE;
/*!40000 ALTER TABLE `lecturas` DISABLE KEYS */;
set autocommit=0;
INSERT INTO `lecturas` VALUES
(80,'2026-04-10 19:00:21.492247',1,7,'cm'),
(30,'2026-04-10 19:02:39.211932',2,8,'cm'),
(30,'2026-04-10 19:03:12.335880',3,9,'cm'),
(30,'2026-04-10 19:03:16.380916',4,10,'cm'),
(40,'2026-04-10 19:03:40.121287',5,11,'cm'),
(60,'2026-04-13 21:08:07.770158',6,19,'%'),
(65,'2026-04-13 21:08:15.742769',7,27,'%'),
(67,'2026-04-13 21:08:23.567047',8,35,'%'),
(70,'2026-04-13 21:08:36.216960',9,20,'%'),
(71,'2026-04-13 21:08:41.067392',10,28,'%'),
(75,'2026-04-13 21:08:48.358543',11,36,'%'),
(3,'2026-04-13 21:12:16.405575',12,17,'L/min'),
(0,'2026-04-13 21:12:30.538774',13,25,'L/min'),
(0.6,'2026-04-13 21:12:42.712463',14,33,'L/min'),
(3,'2026-04-13 21:16:12.540369',15,18,'bar'),
(0,'2026-04-13 21:16:24.428148',16,26,'bar'),
(4,'2026-04-13 21:16:36.081577',17,34,'bar'),
(22.5,'2026-04-13 22:00:00.000000',18,48,'ºC');
/*!40000 ALTER TABLE `lecturas` ENABLE KEYS */;
UNLOCK TABLES;
commit;

--
-- Table structure for table `sectores`
--

DROP TABLE IF EXISTS `sectores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sectores` (
  `latitud` double DEFAULT NULL,
  `longitud` double DEFAULT NULL,
  `superficie` double DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cultivo` varchar(255) DEFAULT NULL,
  `nombre` varchar(255) DEFAULT NULL,
  `parcela` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sectores`
--

LOCK TABLES `sectores` WRITE;
/*!40000 ALTER TABLE `sectores` DISABLE KEYS */;
set autocommit=0;
INSERT INTO `sectores` VALUES
(37.32,-1.862,0,1,'Control General','Sector 0','Parcela Ayuntamiento'),
(37.321,-1.8621,100,2,'Naranjos','Sector 1','Parcela Ayuntamiento'),
(37.322,-1.8619,100,3,'Lechugas','Sector 2','Parcela Ayuntamiento'),
(37.323,-1.8619,100,4,'Tomates','Sector 3','Parcela Ayuntamiento'),
(37.324,-1.8618,100,5,'Varios','Sector 4','Parcela Ayuntamiento'),
(37.325,-1.8617,0,6,'N/A','Estación Meteorológica','Torre');
/*!40000 ALTER TABLE `sectores` ENABLE KEYS */;
UNLOCK TABLES;
commit;

--
-- Table structure for table `sensores`
--

DROP TABLE IF EXISTS `sensores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sensores` (
  `is_actuador` bit(1) DEFAULT NULL,
  `valor_max` int(11) DEFAULT NULL,
  `valor_min` int(11) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `sector_id` bigint(20) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `nombre` varchar(255) DEFAULT NULL,
  `topicmqtt` varchar(255) DEFAULT NULL,
  `topicmqttact` varchar(255) DEFAULT NULL,
  `ubicacion` varchar(255) DEFAULT NULL,
  `estado` enum('ACTIVO','APAGADO','DESCONECTADO','ENCENDIDO','ERROR','INACTIVO') NOT NULL,
  `tipo` enum('BOMBA','CAUDAL','CONDUCTIVIDAD','DIR_VIENTO','ELECTROVALVULA','EV_NUTRIENTES','HUMEDAD','HUMEDAD_EXTERNA','NIVEL','PLUVIOMETRIA','PRESION','PRESION_EXTERNA','TEMPERATURA','VIENTO') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKewefgnffxkpba2buu71bvtn5q` (`sector_id`),
  CONSTRAINT `FKewefgnffxkpba2buu71bvtn5q` FOREIGN KEY (`sector_id`) REFERENCES `sectores` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sensores`
--

LOCK TABLES `sensores` WRITE;
/*!40000 ALTER TABLE `sensores` DISABLE KEYS */;
set autocommit=0;
INSERT INTO `sensores` VALUES
(0x01,-1,-1,1,1,'Bomba General S0','B0_1','0/1/2','0/1/1','sector 0','ACTIVO','BOMBA'),
(0x01,-1,-1,2,1,'EV Balsa 1 S0','EV0_1','0/2/2','0/2/1','sector 0','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,3,1,'EV Balsa 2 S0','EV0_2','0/3/2','0/3/1','sector 0','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,4,1,'EV Balsa 3 S0','EV0_3','0/4/2','0/4/1','sector 0','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,5,1,'EV Balsa 4 S0','EV0_4','0/5/2','0/5/1','sector 0','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,6,1,'EV Nutrientes S0','EVn0_1_1','0/6/2','0/6/1','sector 0','ACTIVO','ELECTROVALVULA'),
(0x00,90,15,7,1,'Nivel Balsa 0','N0_1','0/100/1','','sector 0','ACTIVO','NIVEL'),
(0x00,90,15,8,1,'Nivel Balsa 1','N1_1','0/101/1','','sector 0','ACTIVO','NIVEL'),
(0x00,90,15,9,1,'Nivel Balsa 2','N2_1','0/102/1','','sector 0','ACTIVO','NIVEL'),
(0x00,90,15,10,1,'Nivel Balsa 3','N3_1','0/103/1','','sector 0','ACTIVO','NIVEL'),
(0x00,90,15,11,1,'Nivel Balsa 4','N4_1','0/104/1','','sector 0','ACTIVO','NIVEL'),
(0x00,10000,0,12,1,'Caudal General S0','C0_1','0/105/1','','sector 0','ACTIVO','CAUDAL'),
(0x00,1000,0,13,1,'Presión General S0','P0_1','0/106/1','','sector 0','ACTIVO','PRESION'),
(0x01,-1,-1,14,2,'Bomba S1','B1_1','1/1/2','1/1/1','sector 1','ACTIVO','BOMBA'),
(0x01,-1,-1,15,2,'Electroválvula 1 S1','EV1_1','1/2/2','1/2/1','sector 1','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,16,2,'Electroválvula 2 S1','EV1_2','1/3/2','1/3/1','sector 1','ACTIVO','ELECTROVALVULA'),
(0x00,10000,0,17,2,'Caudal S1','C1_1','1/101/1','','sector 1','ACTIVO','CAUDAL'),
(0x00,1000,0,18,2,'Presión S1','P1_1','1/102/1','','sector 1','ACTIVO','PRESION'),
(0x00,100,0,19,2,'Humedad Suelo 1 S1','H1_1_1','1/110/1','','sector 1','ACTIVO','HUMEDAD'),
(0x00,100,0,20,2,'Humedad Suelo 2 S1','H1_2_1','1/120/1','','sector 1','ACTIVO','HUMEDAD'),
(0x00,1023,0,21,2,'Conductividad S1','Cn1_1','1/103/1','','sector 1','ACTIVO','CONDUCTIVIDAD'),
(0x01,-1,-1,22,3,'Bomba S2','B2_1','2/1/2','2/1/1','sector 2','ACTIVO','BOMBA'),
(0x01,-1,-1,23,3,'Electroválvula 1 S2','EV2_1','2/2/2','2/2/1','sector 2','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,24,3,'Electroválvula 2 S2','EV2_2','2/3/2','2/3/1','sector 2','ACTIVO','ELECTROVALVULA'),
(0x00,10000,0,25,3,'Caudal S2','C2_1','2/101/1','','sector 2','ACTIVO','CAUDAL'),
(0x00,1000,0,26,3,'Presión S2','P2_1','2/102/1','','sector 2','ACTIVO','PRESION'),
(0x00,100,0,27,3,'Humedad Suelo 1 S2','H2_1_1','2/110/1','','sector 2','ACTIVO','HUMEDAD'),
(0x00,100,0,28,3,'Humedad Suelo 2 S2','H2_2_1','2/120/1','','sector 2','ACTIVO','HUMEDAD'),
(0x00,1023,0,29,3,'Conductividad S2','Cn2_1','2/103/1','','sector 2','ACTIVO','CONDUCTIVIDAD'),
(0x01,-1,-1,30,4,'Bomba S3','B3_1','3/1/2','3/1/1','sector 3','ACTIVO','BOMBA'),
(0x01,-1,-1,31,4,'Electroválvula 1 S3','EV3_1','3/2/2','3/2/1','sector 3','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,32,4,'Electroválvula 2 S3','EV3_2','3/3/2','3/3/1','sector 3','ACTIVO','ELECTROVALVULA'),
(0x00,10000,0,33,4,'Caudal S3','C3_1','3/101/1','','sector 3','ACTIVO','CAUDAL'),
(0x00,1000,0,34,4,'Presión S3','P3_1','3/102/1','','sector 3','ACTIVO','PRESION'),
(0x00,100,0,35,4,'Humedad Suelo 1 S3','H3_1_1','3/110/1','','sector 3','ACTIVO','HUMEDAD'),
(0x00,100,0,36,4,'Humedad Suelo 2 S3','H3_2_1','3/120/1','','sector 3','ACTIVO','HUMEDAD'),
(0x00,1023,0,37,4,'Conductividad S3','Cn3_1','3/103/1','','sector 3','ACTIVO','CONDUCTIVIDAD'),
(0x01,-1,-1,38,5,'Bomba S4','B4_1','4/1/2','4/1/1','sector 4','ACTIVO','BOMBA'),
(0x01,-1,-1,39,5,'Electroválvula 1 S4','EV4_1','4/2/2','4/2/1','sector 4','ACTIVO','ELECTROVALVULA'),
(0x01,-1,-1,40,5,'Electroválvula 2 S4','EV4_2','4/3/2','4/3/1','sector 4','ACTIVO','ELECTROVALVULA'),
(0x00,10000,0,41,5,'Caudal S4','C4_1','4/101/1','','sector 4','ACTIVO','CAUDAL'),
(0x00,1000,0,42,5,'Presión S4','P4_1','4/102/1','','sector 4','ACTIVO','PRESION'),
(0x00,100,0,43,5,'Humedad Suelo 1 S4','H4_1_1','4/110/1','','sector 4','ACTIVO','HUMEDAD'),
(0x00,100,0,44,5,'Humedad Suelo 2 S4','H4_2_1','4/120/1','','sector 4','ACTIVO','HUMEDAD'),
(0x00,1023,0,45,5,'Conductividad S4','Cn4_1','4/103/1','','sector 4','ACTIVO','CONDUCTIVIDAD'),
(0x00,1023,0,46,6,'Dirección Viento','DV3','100/1/1','','estacion','ACTIVO','DIR_VIENTO'),
(0x00,1023,0,47,6,'Pluviometría','Pl3','100/2/1','','estacion','ACTIVO','PLUVIOMETRIA'),
(0x00,1023,0,48,6,'Temperatura','T3','100/3/1','','estacion','ACTIVO','TEMPERATURA'),
(0x00,1023,0,49,6,'Humedad Externa','HEE3','100/4/1','','estacion','ACTIVO','HUMEDAD_EXTERNA'),
(0x00,1023,0,50,6,'Presión Externa','PEE3','100/5/1','','estacion','ACTIVO','PRESION_EXTERNA');
/*!40000 ALTER TABLE `sensores` ENABLE KEYS */;
UNLOCK TABLES;
commit;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*M!100616 SET NOTE_VERBOSITY=@OLD_NOTE_VERBOSITY */;

-- Dump completed on 2026-04-19 15:16:05
