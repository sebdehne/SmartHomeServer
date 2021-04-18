#include <Arduino.h>

void resetRN2483();

void setup()
{
  Serial.begin(115200);
  Serial1.begin(57600);
}

void loop()
{
  int i;
  while (Serial.available())
  {
    i = Serial.read();
    if (i < 0)
      break;

    if (i == '!')
    {
      resetRN2483();
      break;
    }

    Serial1.write(i);
  }

  while (Serial1.available())
  {
    i = Serial1.read();
    if (i < 0)
      break;

    Serial.write(i);
  }
}

void resetRN2483()
{
  // reset RN RN2483
  pinMode(8, OUTPUT);
  digitalWrite(8, LOW);
  delay(500);
  digitalWrite(8, HIGH);
  delay(100);
}