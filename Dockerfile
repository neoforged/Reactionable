FROM openjdk:21
COPY build/libs/reactionable-all.jar /reactionable.jar
VOLUME ["/home/reactionable"]
WORKDIR /home/reactionable
RUN cd /home/reactionable
ENTRYPOINT ["java", "-jar", "/reactionable.jar"]
