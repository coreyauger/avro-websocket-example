const avro = require('avsc');

/** A UUID.  Note this is not a */
export type UUID = string;
export type ProviderKey = string;

export const zeroId = "00000000-0000-0000-0000-000000000000"

export interface Model{
    readonly $type: string;
}

export interface Provider extends Model{
    readonly id: string;    
}
export interface Email extends Provider{
    readonly $type: "m.u.Email";        
}
export interface Phone extends Provider{
    readonly $type: "m.u.Phone";        
}

/** A Identity */
export interface Identity extends Model {    
    readonly team: UUID;
    readonly name: string;    
    readonly providers: Provider[];
}
export interface User extends Identity{
    readonly id: UUID 
    readonly avatar: string
    readonly crm: any[]    
}

const user1 = {
  $type: "m.u.User",
  id: zeroId,
  name: "roger",
  avatar: "fart.jpg",
  crm: [],
  team: zeroId,
  providers:[
    {$type: "m.u.Email", id:"test@test.com"},
    {$type: "m.u.Email", id:"test@test2.com"}
  ]
}

const userType = avro.Type.forValue(user1);

// We can use `type` to encode any values with the same structure:
const bufs = [
  userType.toBuffer(user1)
];



console.log("bufs: ", bufs)

console.log("back: ", userType.fromBuffer(bufs[0]))