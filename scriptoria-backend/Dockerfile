# Use official Java image
FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /app

# Copy everything
COPY . .

# Build the project
RUN apt-get update && apt-get install -y maven && mvn clean package -DskipTests

# Run the jar
CMD ["java", "-jar", "target/*.jar"]
