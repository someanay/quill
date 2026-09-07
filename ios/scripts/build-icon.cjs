// Render the actual Android adaptive-icon vector into Apple's required opaque 1024px icon.
// Requires the standard sharp package. No remote artwork or fonts are downloaded.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const xml = fs.readFileSync(path.join(root, 'app/src/main/res/drawable/ic_launcher_foreground.xml'), 'utf8');
const shapes = [...xml.matchAll(/<path\s+([^>]+)\/>/g)].map(([, attributes]) => {
  const attr = Object.fromEntries([...attributes.matchAll(/android:(\w+)="([^"]*)"/g)].map(([, name, value]) => [name, value]));
  return `<path d="${attr.pathData}" fill="${attr.fillColor}" stroke="${attr.strokeColor}" stroke-width="${attr.strokeWidth}" stroke-linejoin="round"/>`;
}).join('');
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 108 108"><rect width="108" height="108" fill="#F5F7F5"/>${shapes}</svg>`;
const folder = path.join(root, 'ios/Quill/Resources/Assets.xcassets/AppIcon.appiconset');
fs.mkdirSync(folder, {recursive: true});
sharp(Buffer.from(svg)).removeAlpha().png().toFile(path.join(folder, 'AppIcon.png')).then(() => {
  fs.writeFileSync(path.join(folder, 'Contents.json'), JSON.stringify({images: [{filename: 'AppIcon.png', idiom: 'universal', platform: 'ios', size: '1024x1024'}], info: {author: 'xcode', version: 1}}, null, 2) + '\n');
  fs.writeFileSync(path.dirname(folder) + '/Contents.json', JSON.stringify({info: {author: 'xcode', version: 1}}, null, 2) + '\n');
  console.log('Rendered Android vector as the iOS app icon.');
}).catch(error => { console.error(error.message); process.exitCode = 1; });
