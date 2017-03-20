# A Travis CI configuration file.

sudo: required

language: scala
scala:
   - 2.11.8

services:
  - docker

env:
  global:
    - TERM=dumb

notifications:
  email: false
  slack:
    rooms:
      - secure: "Rrj5ya7JV7bfgW3GXZpuRsX2e9/qsTpLLVZAzgq2Z3xW/IVAqeAM8DDR0tjPbR7Nou0RpPtE/ynkps4Qd6CGTBiCULpcGi2cXJ+p35WvaweWlFIKU0Vam9iEWXQ+bG1LwB61LrSmD7DJvOxkZiwtagqUfYLhVeD1WfgcWc/lj0F0julCScujqqJbj/jUnlkSm6VJz9m11NQkvZZ+YFgNYCPAQRmSh59K0ELximJ54C4/af05YHq6QLWUh/2Eoa68p3XzQyONtteMRvbAJgmMGIijQsKa+ZELrZPjz/5EplMA+FEUjoW6tn3NQjQgZOdDHq3fEbnuit0OOBMRu5QhYT0MjV6QJANYmJU2QqVoDubgTtQSeHJNYoM1fI15yfuSCJg+H6FnI0ncXRB3zz7EZ3zksKKeWZ20NQVKBNCYRNIBC8Im3ff8TbZdulH/Qk/NEeOJwpgm8yfUqUqLu3dt13KT0JdxEcQeOX5lRmz+XBrN6C52/fX0NstO2OCxpKhKcq2+B/Y3mCn6ywqAiTtaLyhPrW08cCB3sBX8y5/Q3do8KPWO1MiBc/K+qtK481bnkh41DaLasqpltpEkFhCW/Udl8iuYEkMWZwNdhEUv6uuPiKF9da4FJkhIV3xVYOzrjF6xR2BtmHVGnCVMMoCUQo9tG8K9tOf+omH+KezZPuw="
      - secure: "WgdL9/Z/9+rl6edh+mqDiO0mx8JpxYwIrudYwuZUZ7d/9oUzS8Miid7au7Y1PP0CVPVDEvastw6+NnIVA/BIHTBNj488dTeNXumaaNQlaTTvaI9OLN1w3+GVVFuF64gmJ6I2T6x+qxqlYOwQhyZ8jZEJUXQ3VwE6DPz+KNqpBy+6GgWEvi+2lgGhc2Ko3SPAtcDWAdE6t8ELaO2uLh/JU60ndAi4yT297fOfwWi4I1aee5a+LajbJN6Mz89o5o0Y2GHV83a3D/ablt1CH3kRSErqLV3HDbt5eRR4yFhTPpbZG8yeWcZkP0oKyt3QqSen1paiEeqr7R9OspDf2OLSlWx7TGm/BnNW/3YRI3+gUPHqaJPCLldNRWnbYYd0RFGjVOfVOoYhAWAMdkWT48jX3YGE1LaZ0SwlxpMy5Fpj8Q22SVXY51o5Pw1UomUhGz2Hjfj8b0WbkWQ+q5qpgR8/z4Zfz5i3MY03mQNzeXs4Z9NoznIiz+9eQVpi4tkjUH3o2AFi6VXTdire61vfhFG3iYYqo2woKwNWOVaK/g0l13EVWiK62YxQEUZJbmhLb6VQrABrB2FCWl/+zZOOR3OUeie3AgAmxEwjxN9Y1IgMy0bJWEZ+ECf//39XMgj5Ys/gPsejDjPsWxhO6cuNtGq9K6RsPij1a0E+01JcyrNyNX0="
    on_pull_requests: false
    on_success: change
    on_failure: always
  webhooks:
    urls:
      secure: "nbPjG0Y+P6tbEcQ+Q3k3EYv46IhQQJvRpEr/090H7A349cdn3vJRxSVKn1CtcboN7A+grhSu0Dx7jS8BwlRcOt1iqFAEDMOyN+6QcFGvpAGv3P2vZ9ML/yArH+c7mZ6k2X+BKdpqwQhrsaCwq23OAqKlP00T+S+exSmP9y3F3M6xVgCFP1RRlBTJfJiNnSir2vblskiXtAxHsO9jz/4cCxNGtzxmGD5Lhm6dchgwe7wyUCyhK8RG1NXc/z9x0mFernvPQBJJCn36NoijqbFiji1o8BaVULJszs+Gkd7rQxAntMm2wUQYWLsDTFp+V1HzgkdRHx4GpfoeK+aufGXS1r+kqGpsnMYqAqCQxtbATR7oB7XKlDjmiyFUkHng1ODDoUHp9k01Ow7CvCpwURlVglAOqmwM/+HEbUEvzrA1e5XNTn5auOJ0YsvZ9/SKToXmoZhYBnel0IVTQc2jFpp6obQEGX/0kjRbEQpdi8troSMSYpuCTeQPa0Cxazp9hICPbE7llOcEEA4odyj9NnINPiGAFowBeJNA4u5dln9RlAuNYsyNgMtmN39D5dc/YWri0M7EaWQ9e0Qn9AIDsAf/o7cHg2vcU1kNxp3FM/WyVjwQCV7lfgm9Q7hD1wJP4EdkCg2mDPMfad3eGrWW2AKxv8XYANQo495ODukRdLNLBfk="

before_install:
  - pip install --upgrade pip setuptools
  - pip3 install --upgrade pip setuptools
  - ./tools/travis/flake8.sh  # Check Python files for style and stop the build on syntax errors

install:
  - ./tools/travis/setup.sh

script:
  - ./tools/travis/build.sh
  - ./tools/travis/box-upload.py "/home/travis/build/openwhisk/openwhisk/logs" "$TRAVIS_BUILD_ID-$TRAVIS_BRANCH.tar.gz"
