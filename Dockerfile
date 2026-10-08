FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY java/MrPhoneServer.java .
RUN javac --release 17 --add-modules jdk.httpserver MrPhoneServer.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/*.class .
ENV PORT=10000
EXPOSE 10000
CMD ["java", "--add-modules", "jdk.httpserver", "MrPhoneServer"]
