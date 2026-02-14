FROM docker.io/eclipse-temurin:25-jre-jammy
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

# podman build -t springbootapp:1.0 .
# podman image list
# podman run -p 8080:8080 --network=host --name springapiejemplo -d -t springbootapp:1.0
# podman stop springapiejemplo
# podman start springapiejemplo
# podman stats
# podman logs -f springapp
# podman rm springapp
# podman login
# podman tag springbootapp:1.0 alansastre/springbootapp:1.0
# podman push alansastre/springbootapp:1.0

# Desde un servidor:
# podman pull alansastre/springbootapp:1.0
# podman run -p 8080:8080 --name springapp -d -t alansastre/springbootapp:1.0
# podman rmi alansastre/springbootapp:1.0