// Shared by Focus Guard and the Keeper: keep the other extension enabled.
function guard(id) {
  const enable = () => chrome.management.setEnabled(id, true).catch(() => {});
  chrome.management.onDisabled.addListener(info => info.id === id && enable());
  chrome.runtime.onStartup.addListener(enable);
  chrome.runtime.onInstalled.addListener(enable);
  chrome.alarms.create("guard", { periodInMinutes: 1 });
  chrome.alarms.onAlarm.addListener(a => a.name === "guard" && enable());
}
