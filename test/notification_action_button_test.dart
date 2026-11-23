import 'package:alarm/alarm.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const withAction = NotificationSettings(
    title: 'Waktu Solat',
    body: 'Masuk waktu Zohor',
    stopButton: 'TUTUP',
    androidActionButton: 'Sudah solat Zohor',
    androidActionPayload: '{"date":"2026-11-23","trackerName":"Zohor"}',
  );

  test('action button and payload survive a JSON round trip', () {
    final restored = NotificationSettings.fromJson(withAction.toJson());
    expect(restored, withAction);
    expect(restored.androidActionButton, 'Sudah solat Zohor');
    expect(restored.androidActionPayload, contains('Zohor'));
  });

  test('alarms stored before the fields existed still decode without them', () {
    final json = withAction.toJson()
      ..remove('androidActionButton')
      ..remove('androidActionPayload');
    final restored = NotificationSettings.fromJson(json);
    expect(restored.androidActionButton, isNull);
    expect(restored.androidActionPayload, isNull);
  });

  test('both reach the platform', () {
    final wire = withAction.toWire();
    expect(wire.androidActionButton, 'Sudah solat Zohor');
    expect(wire.androidActionPayload, withAction.androidActionPayload);
  });

  test('copyWith can clear the action', () {
    final cleared = withAction.copyWith(androidActionButton: () => null);
    expect(cleared.androidActionButton, isNull);
    expect(cleared.androidActionPayload, withAction.androidActionPayload);
  });
}
