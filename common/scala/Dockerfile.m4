FROM m4_ifdef(`S390X',`s390x/ibmjava:sfj',`buildpack-deps:trusty-curl')

m4_ifdef(`S390X',`
#----------------------- IBM Java Installation for System Z architecture ---------------------------
#
#  Pulled directly from the java image above.  Hopefully docker lets us get away with NO-OP
#
#----------------------- IBM Java Installation for System Z architecture ---------------------------
',`
#---------------------- Oracle Java Installation from here Onward -----------------------------------
ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update \
 && apt-get -y install locales \
 && locale-gen en_US.UTF-8 \
 && apt-get clean && rm -rf /var/lib/apt/lists/

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ENV VERSION 8
ENV UPDATE 141
ENV BUILD 15
ENV SIG 336fa29ff2bb4ef291e347e091f7f4a7

ENV JAVA_HOME /usr/lib/jvm/java-${VERSION}-oracle
ENV JRE_HOME ${JAVA_HOME}/jre
ENV PATH $JAVA_HOME/bin:$PATH

RUN curl --silent --location --retry 3 --cacert /etc/ssl/certs/GeoTrust_Global_CA.pem \
  --header "Cookie: oraclelicense=accept-securebackup-cookie;" \
  http://download.oracle.com/otn-pub/java/jdk/"${VERSION}"u"${UPDATE}"-b"${BUILD}"/"${SIG}"/jdk-"${VERSION}"u"${UPDATE}"-linux-x64.tar.gz \
  | tar xz -C /tmp && \
  mkdir -p /usr/lib/jvm && mv /tmp/jdk1.${VERSION}.0_${UPDATE} "${JAVA_HOME}" && \
  apt-get autoclean && apt-get --purge -y autoremove && \
  rm -rf /tmp/* /var/tmp/*

RUN update-alternatives --install "/usr/bin/java" "java" "${JRE_HOME}/bin/java" 1 && \
  update-alternatives --install "/usr/bin/javac" "javac" "${JAVA_HOME}/bin/javac" 1 && \
  update-alternatives --set java "${JRE_HOME}/bin/java" && \
  update-alternatives --set javac "${JAVA_HOME}/bin/javac" && \
  mkdir /logs
')
